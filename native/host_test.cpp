#include "engine.h"
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
int main(int argc,char **argv){
 try{
  if(argc<3)return 2;
  auto loaded=std::chrono::steady_clock::now();TranslationEngine engine(argv[1]);
  std::cout<<"LOAD ms="<<std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-loaded).count()<<std::endl;
  bool failed=false;
  for(int i=2;i<argc;i++){
   std::ifstream f(argv[i]);if(!f){std::cerr<<"Missing sample file"<<std::endl;return 2;}std::stringstream s;s<<f.rdbuf();auto start=std::chrono::steady_clock::now();
   try{auto out=engine.translate(s.str());std::cout<<"SAMPLE "<<i-1<<" ms="<<std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count()<<"\n"<<out<<std::endl;}
   catch(const std::exception &e){failed=true;std::cerr<<"SAMPLE "<<i-1<<" ERROR "<<e.what()<<std::endl;}
  }
  return failed?1:0;
 }catch(const std::exception &e){std::cerr<<e.what()<<std::endl;return 1;}
}
