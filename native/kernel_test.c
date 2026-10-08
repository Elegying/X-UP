#define GGML_COMMON_DECL_C
#include "ggml-common.h"
#include "ggml-quants.h"
#include "ggml-cpu/quants.h"
#include "ggml.h"
#include <math.h>
#include <stdio.h>
#include <stdint.h>
extern void ggml_vec_dot_stq1_0_q8_K_generic(int,float*,size_t,const void*,size_t,const void*,size_t,int);
int main(void) {
 struct ggml_init_params params={1024*1024,NULL,true};struct ggml_context *ctx=ggml_init(params);
 uint32_t seed=12345;int checked=0;
 for(int trial=0;trial<32;trial++) {
  float x[1024],y[1024],dx[1024],dy[1024];block_stq1_0 qx[4];block_q8_K qy[4];
  for(int i=0;i<1024;i++){seed=seed*1664525u+1013904223u;x[i]=((int)(seed%2001)-1000)/500.f;seed=seed*1664525u+1013904223u;y[i]=((int)(seed%2001)-1000)/300.f;}
  quantize_row_stq1_0_ref(x,qx,1024);quantize_row_q8_K_ref(y,qy,1024);dequantize_row_stq1_0(qx,dx,1024);dequantize_row_q8_K(qy,dy,1024);
  double expected=0;for(int i=0;i<1024;i++)expected+=(double)dx[i]*dy[i];
  float selected=0,scalar=0;ggml_vec_dot_stq1_0_q8_K(1024,&selected,0,qx,0,qy,0,1);ggml_vec_dot_stq1_0_q8_K_generic(1024,&scalar,0,qx,0,qy,0,1);
  if(fabs(selected-expected)>0.002||fabs(scalar-expected)>0.002){fprintf(stderr,"STQ mismatch trial=%d selected=%f scalar=%f expected=%f\n",trial,selected,scalar,expected);return 1;}checked++;
 }
 ggml_free(ctx);printf("STQ: %d randomized scalar/runtime-dispatch/reference comparisons passed\n",checked);return 0;
}
