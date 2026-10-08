#include "opus_engine.h"
#include <chrono>
#include <iostream>
int main(int argc,char**argv){if(argc<2)return 2;OpusEngine e(argv[1]);for(auto text:{"The launch made the impossible look routine.","今日は雨なので、傘を忘れないでください。"}){auto begin=std::chrono::steady_clock::now();std::cout<<e.translate(text,(unsigned char)text[0]>127,e.ticket())<<"\nms="<<std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-begin).count()<<std::endl;}auto old=e.ticket();e.cancel();try{e.translate("must cancel",false,old);return 3;}catch(const std::exception&){std::cout<<"cancel PASS\n";}return 0;}
