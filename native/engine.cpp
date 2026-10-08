#include "engine.h"
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <vector>
struct TranslationEngine::Impl {
 llama_model *model=nullptr;llama_context *context=nullptr;
 std::atomic<uint64_t> epoch{0};uint64_t active_ticket=0;
 std::chrono::steady_clock::time_point deadline;
 ~Impl(){if(context)llama_free(context);if(model)llama_model_free(model);}
 static bool expired(void *p){auto *state=static_cast<Impl*>(p);return state->epoch.load()!=state->active_ticket||std::chrono::steady_clock::now()>state->deadline;}
};
TranslationEngine::TranslationEngine(const std::string &path):impl(new Impl){
 static std::once_flag init;std::call_once(init,[]{
#ifdef __ANDROID__
 llama_log_set([](ggml_log_level,const char*,void*){},nullptr);
#endif
llama_backend_init();});
 auto mp=llama_model_default_params();mp.n_gpu_layers=0;
 impl->model=llama_model_load_from_file(path.c_str(),mp);if(!impl->model)throw std::runtime_error("模型无法加载，请重新下载");
 auto cp=llama_context_default_params();cp.n_ctx=2048;cp.n_batch=256;cp.n_ubatch=128;
 cp.n_threads=std::max(1u,std::min(4u,std::thread::hardware_concurrency()));cp.n_threads_batch=cp.n_threads;
 impl->context=llama_init_from_model(impl->model,cp);if(!impl->context)throw std::runtime_error("手机内存不足，无法启动本地模型");
 llama_set_abort_callback(impl->context,Impl::expired,impl.get());
}
TranslationEngine::~TranslationEngine()=default;
uint64_t TranslationEngine::ticket() const{return impl->epoch.load();}
void TranslationEngine::cancel(){impl->epoch.fetch_add(1);}
std::string TranslationEngine::translate(const std::string &prompt){return translate(prompt,ticket());}
std::string TranslationEngine::translate(const std::string &prompt,uint64_t ticket){
 impl->active_ticket=ticket;if(impl->epoch.load()!=ticket)throw std::runtime_error("翻译已取消");
 auto *ctx=impl->context;auto *vocab=llama_model_get_vocab(impl->model);
 llama_memory_clear(llama_get_memory(ctx),true);impl->deadline=std::chrono::steady_clock::now()+std::chrono::seconds(25);
 // Use the exact single-user template shipped in the pinned HY-MT model.
 std::string rendered="<｜hy_begin▁of▁sentence｜><｜hy_User｜>"+prompt+"<｜hy_Assistant｜>";
 int n=static_cast<int>(rendered.size());
 int count=-llama_tokenize(vocab,rendered.data(),n,nullptr,0,false,true);
 if(count<=0||count>1536)throw std::runtime_error("内容过长，保留原文");
 std::vector<llama_token> tokens(count);if(llama_tokenize(vocab,rendered.data(),n,tokens.data(),tokens.size(),false,true)!=count)throw std::runtime_error("文本编码失败");
 for(int offset=0;offset<count;offset+=256){auto batch=llama_batch_get_one(tokens.data()+offset,std::min(256,count-offset));if(llama_decode(ctx,batch)!=0)throw std::runtime_error("本地翻译未完成，请稍后重试");}
 auto chain=llama_sampler_chain_init(llama_sampler_chain_default_params());
 std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(chain,llama_sampler_free);
 llama_sampler_chain_add(chain,llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.05f,0,0));
 llama_sampler_chain_add(chain,llama_sampler_init_top_k(20));llama_sampler_chain_add(chain,llama_sampler_init_top_p(.6f,1));
 llama_sampler_chain_add(chain,llama_sampler_init_temp(.7f));llama_sampler_chain_add(chain,llama_sampler_init_dist(42));
 std::string output;
 for(int i=0;i<512;i++){
  if(Impl::expired(impl.get()))throw std::runtime_error("本地翻译超时，保留原文");
  llama_token token=llama_sampler_sample(chain,ctx,-1);
  if(llama_vocab_is_eog(vocab,token))return output;
  char part[256];int len=llama_token_to_piece(vocab,token,part,sizeof(part),0,false);
  if(len<0){std::vector<char> large(-len);len=llama_token_to_piece(vocab,token,large.data(),large.size(),0,false);if(len>0)output.append(large.data(),len);}else output.append(part,len);

  if(output.size()>32768)throw std::runtime_error("译文异常，保留原文");
  auto batch=llama_batch_get_one(&token,1);if(llama_decode(ctx,batch)!=0)throw std::runtime_error("本地翻译未完成，请稍后重试");
 }

 throw std::runtime_error("译文未完整生成，保留原文");
}
