#include "engine.h"
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
int main(int argc,char **argv){try{if(argc<3)return 2;TranslationEngine engine(argv[1]);for(int i=2;i<argc;i++){std::ifstream f(argv[i]);std::stringstream s;s<<f.rdbuf();auto start=std::chrono::steady_clock::now();auto out=engine.translate(s.str());std::cout<<"SAMPLE "<<i-1<<" ms="<<std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count()<<"\n"<<out<<"\n";}}catch(const std::exception &e){std::cerr<<e.what()<<"\n";return 1;}}
