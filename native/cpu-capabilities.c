static int available;
#if defined(__ANDROID__)
#include <sys/auxv.h>
#include <asm/hwcap.h>
__attribute__((constructor)) static void detect(void) { available=(getauxval(AT_HWCAP)&HWCAP_ASIMDDP)!=0; }
#elif defined(__APPLE__)
#include <sys/sysctl.h>
__attribute__((constructor)) static void detect(void) { size_t size=sizeof(available); if(sysctlbyname("hw.optional.arm.FEAT_DotProd",&available,&size,0,0)!=0)available=0; }
#endif
int xup_stq_has_dotprod(void) { return available; }
