#include "opus_engine.h"
#include <ctranslate2/translator.h>
#include <sentencepiece_processor.h>
#include <atomic>
#include <chrono>
#include <stdexcept>
#include <thread>
struct OpusEngine::Impl {
 struct Model {
  std::unique_ptr<ctranslate2::Translator> model;
  sentencepiece::SentencePieceProcessor source,target;
  explicit Model(const std::string &path){
   if(!source.Load(path+"/source.spm").ok()||!target.Load(path+"/target.spm").ok())throw std::runtime_error("OPUS 分词模型损坏，请重新下载");
   ctranslate2::ReplicaPoolConfig pool;pool.num_threads_per_replica=std::max(1u,std::min(4u,std::thread::hardware_concurrency()));
   model=std::make_unique<ctranslate2::Translator>(path,ctranslate2::Device::CPU,ctranslate2::ComputeType::INT8,std::vector<int>{0},false,pool);
  }
 };
 std::string path;std::unique_ptr<Model> en,ja;std::atomic<uint64_t> epoch{0};
 std::string run(Model &m,const std::string &text,bool chinese,uint64_t ticket){
  std::vector<std::string> tokens;if(!m.source.Encode(text,&tokens).ok())throw std::runtime_error("OPUS 文本编码失败");
  for(const auto &token:tokens)
   if(m.source.PieceToId(token)==m.source.unk_id())throw std::runtime_error("OPUS 输入含模型不支持的字符，保留原文");
  if(chinese)tokens.insert(tokens.begin(),">>cmn_Hans<<");tokens.push_back("</s>");
  if(tokens.size()>480)throw std::runtime_error("段落过长，保留原文");
  auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(12);
  ctranslate2::TranslationOptions options;options.beam_size=1;options.max_input_length=512;options.max_decoding_length=384;
  options.callback=[&,this](ctranslate2::GenerationStepResult){return epoch.load()!=ticket||std::chrono::steady_clock::now()>deadline;};
  auto result=m.model->translate_batch({tokens},options);
  if(epoch.load()!=ticket||std::chrono::steady_clock::now()>deadline)throw std::runtime_error("翻译已取消或超时");
  if(result.empty()||result[0].hypotheses.empty()||result[0].hypotheses[0].size()>=options.max_decoding_length)throw std::runtime_error("OPUS 未生成完整译文");
  // Reject unknown tokens before SentencePiece renders them as U+2047, including JA->EN pivot.
  for(const auto &token:result[0].hypotheses[0])
   if(token=="<unk>")throw std::runtime_error("OPUS 译文含未知字符，保留原文");
  std::string out;if(!m.target.Decode(result[0].hypotheses[0],&out).ok())throw std::runtime_error("OPUS 译文解码失败");return out;
 }
};
OpusEngine::OpusEngine(const std::string &path):impl(new Impl){impl->path=path;impl->en=std::make_unique<Impl::Model>(path+"/en-zh");}
OpusEngine::~OpusEngine()=default;
uint64_t OpusEngine::ticket() const{return impl->epoch.load();}
void OpusEngine::cancel(){impl->epoch.fetch_add(1);}
std::string OpusEngine::translate(const std::string &text,bool japanese,uint64_t ticket){
 if(impl->epoch.load()!=ticket)throw std::runtime_error("翻译已取消");
 std::string input=text;
 if(japanese){if(!impl->ja)impl->ja=std::make_unique<Impl::Model>(impl->path+"/ja-en");input=impl->run(*impl->ja,input,false,ticket);}
 return impl->run(*impl->en,input,true,ticket);
}
