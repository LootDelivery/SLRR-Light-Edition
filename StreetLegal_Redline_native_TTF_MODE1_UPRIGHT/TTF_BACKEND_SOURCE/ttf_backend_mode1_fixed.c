/*
 * SLRR native runtime TTF backend (PE32/x86, freestanding C).
 *
 * Replaces the GfxDevice virtual methods DrawText (+0x204) and MeasureText
 * (+0x214) through the stock GfxDevice vtable.  The stock bitmap backend is
 * retained as fallback.  TTF glyphs are rasterized on demand with Win32 GDI
 * and uploaded directly to managed D3D9 textures; no SCX/RPK/TGA/PNG files
 * are generated or modified.
 */
#include <stdint.h>

typedef uint8_t  U8;
typedef uint16_t U16;
typedef uint32_t U32;
typedef int32_t  I32;
typedef int16_t  I16;
typedef U32      ULONG;
typedef uint64_t U64;
typedef int BOOL;
typedef void *HANDLE;
typedef void *HMODULE;
typedef void *HDC;
typedef void *HFONT;
typedef void *HGDIOBJ;
typedef void *LPVOID;
typedef const char *LPCSTR;
typedef const U16 *LPCWSTR;
typedef U32 DWORD;
typedef U32 UINT;
typedef long HRESULT;
typedef struct { I32 left, top, right, bottom; } RECT;
typedef struct { I32 Pitch; void *pBits; } D3DLOCKED_RECT;

#define TTFSEC __attribute__((section(".text"), noinline))
#define STDCALL __attribute__((stdcall))
#define THISCALL __attribute__((thiscall))
#define NULL ((void*)0)
#define TRUE 1
#define FALSE 0
#define INVALID_HANDLE_VALUE ((HANDLE)(intptr_t)-1)
#define GENERIC_READ 0x80000000u
#define GENERIC_WRITE 0x40000000u
#define FILE_SHARE_READ 1u
#define FILE_SHARE_WRITE 2u
#define OPEN_EXISTING 3u
#define CREATE_ALWAYS 2u
#define FILE_ATTRIBUTE_NORMAL 0x80u
#define FR_PRIVATE 0x10u
#define GGO_GRAY8_BITMAP 6u
#define GDI_ERROR 0xffffffffu
#define FW_NORMAL 400
#define DEFAULT_CHARSET 1
#define OUT_DEFAULT_PRECIS 0
#define CLIP_DEFAULT_PRECIS 0
#define ANTIALIASED_QUALITY 4
#define DEFAULT_PITCH 0
#define FF_DONTCARE 0
#define D3DFMT_A8R8G8B8 21u
#define D3DPOOL_MANAGED 1u
#define D3DPT_TRIANGLESTRIP 5u
#define D3DFVF_XYZRHW 0x004u
#define D3DFVF_DIFFUSE 0x040u
#define D3DFVF_TEX1 0x100u
#define D3DBLEND_SRCALPHA 5u
#define D3DBLEND_INVSRCALPHA 6u
#define D3DCULL_NONE 1u
#define D3DTOP_MODULATE 4u
#define D3DTA_DIFFUSE 0u
#define D3DTA_TEXTURE 2u
#define D3DTEXF_LINEAR 2u
#define D3DRS_ZENABLE 7u
#define D3DRS_ZWRITEENABLE 14u
#define D3DRS_SRCBLEND 19u
#define D3DRS_DESTBLEND 20u
#define D3DRS_CULLMODE 22u
#define D3DRS_ALPHABLENDENABLE 27u
#define D3DRS_ALPHATESTENABLE 15u
#define D3DRS_LIGHTING 137u
#define D3DTSS_COLOROP 1u
#define D3DTSS_COLORARG1 2u
#define D3DTSS_COLORARG2 3u
#define D3DTSS_ALPHAOP 4u
#define D3DTSS_ALPHAARG1 5u
#define D3DTSS_ALPHAARG2 6u
#define D3DLOCK_NOSYSLOCK 0x800u
#define D3DLOCK_DISCARD 0x2000u
#define ATLAS_SIZE 1024u
#define GLYPH_HASH_SIZE 2048u
#define KERN_HASH_SIZE 4096u
#define FONT_CACHE_SLOTS 129u
#define MAX_TEXT_CHARS 8192u

#pragma pack(push,4)
typedef struct { U16 fract; I16 value; } FIXED;
typedef struct { FIXED eM11,eM12,eM21,eM22; } MAT2;
typedef struct { I32 x,y; } POINTL;
typedef struct {
  I32 gmBlackBoxX, gmBlackBoxY;
  POINTL gmptGlyphOrigin;
  I16 gmCellIncX, gmCellIncY;
} GLYPHMETRICS;
typedef struct {
  I32 tmHeight, tmAscent, tmDescent, tmInternalLeading, tmExternalLeading;
  I32 tmAveCharWidth, tmMaxCharWidth, tmWeight, tmOverhang;
  I32 tmDigitizedAspectX, tmDigitizedAspectY;
  U16 tmFirstChar, tmLastChar, tmDefaultChar, tmBreakChar;
  U8 tmItalic, tmUnderlined, tmStruckOut, tmPitchAndFamily, tmCharSet;
} TEXTMETRICW;
typedef struct {
  I32 lfHeight,lfWidth,lfEscapement,lfOrientation,lfWeight;
  U8 lfItalic,lfUnderline,lfStrikeOut,lfCharSet,lfOutPrecision,lfClipPrecision,lfQuality,lfPitchAndFamily;
  U16 lfFaceName[32];
} LOGFONTW;
typedef struct { U16 wFirst, wSecond; I32 iKernAmount; } KERNINGPAIR;
#pragma pack(pop)

typedef HANDLE (STDCALL *PFN_CreateFileA)(LPCSTR,DWORD,DWORD,void*,DWORD,DWORD,HANDLE);
typedef BOOL (STDCALL *PFN_ReadFile)(HANDLE,LPVOID,DWORD,DWORD*,void*);
typedef BOOL (STDCALL *PFN_WriteFile)(HANDLE,const void*,DWORD,DWORD*,void*);
typedef BOOL (STDCALL *PFN_CloseHandle)(HANDLE);
typedef DWORD (STDCALL *PFN_GetFileSize)(HANDLE,DWORD*);
typedef DWORD (STDCALL *PFN_GetModuleFileNameA)(HMODULE,char*,DWORD);
typedef HMODULE (STDCALL *PFN_LoadLibraryA)(LPCSTR);
typedef void *(STDCALL *PFN_GetProcAddress)(HMODULE,LPCSTR);
typedef HANDLE (STDCALL *PFN_GetProcessHeap)(void);
typedef LPVOID (STDCALL *PFN_HeapAlloc)(HANDLE,U32,U32);
typedef BOOL (STDCALL *PFN_HeapFree)(HANDLE,U32,LPVOID);
typedef BOOL (STDCALL *PFN_DeleteObject)(HGDIOBJ);
typedef I32 (STDCALL *PFN_MultiByteToWideChar)(U32,DWORD,const char*,I32,U16*,I32);

typedef U32 (STDCALL *PFN_AddFontResourceExW)(LPCWSTR,DWORD,void*);
typedef HDC (STDCALL *PFN_CreateCompatibleDC)(HDC);
typedef BOOL (STDCALL *PFN_DeleteDC)(HDC);
typedef HFONT (STDCALL *PFN_CreateFontIndirectW)(const LOGFONTW*);
typedef HGDIOBJ (STDCALL *PFN_SelectObject)(HDC,HGDIOBJ);
typedef BOOL (STDCALL *PFN_GetTextMetricsW)(HDC,TEXTMETRICW*);
typedef DWORD (STDCALL *PFN_GetGlyphOutlineW)(HDC,UINT,UINT,GLYPHMETRICS*,DWORD,void*,const MAT2*);
typedef DWORD (STDCALL *PFN_GetKerningPairsW)(HDC,DWORD,KERNINGPAIR*);
typedef BOOL (STDCALL *PFN_GetCharWidth32W)(HDC,UINT,UINT,I32*);
typedef I32 (STDCALL *PFN_SetUnhandledExceptionFilter)(void*);
typedef BOOL (STDCALL *PFN_FlushFileBuffers)(HANDLE);

typedef HRESULT (STDCALL *PFN_D3DGetTexture)(void*,DWORD,void**);
typedef HRESULT (STDCALL *PFN_D3DSetTexture)(void*,DWORD,void*);
typedef HRESULT (STDCALL *PFN_D3DGetRenderState)(void*,DWORD,DWORD*);
typedef HRESULT (STDCALL *PFN_D3DSetRenderState)(void*,DWORD,DWORD);
typedef HRESULT (STDCALL *PFN_D3DGetTSS)(void*,DWORD,DWORD,DWORD*);
typedef HRESULT (STDCALL *PFN_D3DSetTSS)(void*,DWORD,DWORD,DWORD);
typedef HRESULT (STDCALL *PFN_D3DGetSampler)(void*,DWORD,DWORD,DWORD*);
typedef HRESULT (STDCALL *PFN_D3DSetSampler)(void*,DWORD,DWORD,DWORD);
typedef HRESULT (STDCALL *PFN_D3DGetFVF)(void*,DWORD*);
typedef HRESULT (STDCALL *PFN_D3DSetFVF)(void*,DWORD);
typedef HRESULT (STDCALL *PFN_D3DGetObject)(void*,void**);
typedef HRESULT (STDCALL *PFN_D3DSetObject)(void*,void*);
typedef HRESULT (STDCALL *PFN_D3DGetStreamSource)(void*,UINT,void**,UINT*,UINT*);
typedef HRESULT (STDCALL *PFN_D3DSetStreamSource)(void*,UINT,void*,UINT,UINT);
typedef HRESULT (STDCALL *PFN_D3DDrawUP)(void*,DWORD,UINT,const void*,UINT);
typedef HRESULT (STDCALL *PFN_D3DCreateTexture)(void*,UINT,UINT,UINT,DWORD,DWORD,DWORD,void**,HANDLE*);
typedef HRESULT (STDCALL *PFN_TexLockRect)(void*,UINT,D3DLOCKED_RECT*,const RECT*,DWORD);
typedef HRESULT (STDCALL *PFN_TexUnlockRect)(void*,UINT);
typedef ULONG (STDCALL *PFN_Release)(void*);
typedef BOOL (THISCALL *PFN_FontStreamLock)(void*,U32,U32,void**,U32);
typedef void (THISCALL *PFN_FontStreamUnlock)(void*);

typedef struct {
  U32 codepoint;
  U16 x,y,w,h;
  I16 bearing_x,bearing_y;
  I16 advance;
  U16 used;
} GlyphRec;
typedef struct { U32 key; I16 amount; U16 used; } KernRec;
typedef struct {
  U32 px;
  I32 ascent, descent, line_height;
  HDC dc;
  HFONT font;
  HGDIOBJ old_font;
  void *texture;
  GlyphRec *glyphs;
  KernRec *kern;
  U32 pen_x, pen_y, row_h, glyph_count;
  U32 ready;
} FontCache;
typedef struct { float x,y,z,rhw; DWORD color; float u,v; } TTFVertex;
typedef struct { void *stream; U32 px; U32 valid; } Mode1FontSize;

#define IAT_LOADLIBRARYA 0x005e70d8u
#define IAT_GETPROCADDRESS 0x005e70dcu
#define IAT_HEAPALLOC 0x005e7100u
#define IAT_GETPROCESSHEAP 0x005e7104u
#define IAT_HEAPFREE 0x005e7108u
#define IAT_WRITEFILE 0x005e7060u
#define IAT_CREATEFILEA 0x005e7194u
#define IAT_GETFILESIZE 0x005e7198u
#define IAT_READFILE 0x005e719cu
#define IAT_CLOSEHANDLE 0x005e7118u
#define IAT_GETMODULEFILENAMEA 0x005e7188u
#define IAT_MULTIBYTETOWIDECHAR 0x005e7114u
#define D3D_DEVICE_PTR 0x006495e4u
#define GFXDEVICE_PTR 0x006495dcu

#define IAT(type,addr) (*(volatile type *)(addr))

static HANDLE gHeap;
static char gRoot[260];
static U16 gFace[32];
static U16 gTtfPath[520];
static PFN_AddFontResourceExW pAddFontResourceExW;
static PFN_CreateCompatibleDC pCreateCompatibleDC;
static PFN_DeleteDC pDeleteDC;
static PFN_CreateFontIndirectW pCreateFontIndirectW;
static PFN_SelectObject pSelectObject;
static PFN_DeleteObject pDeleteObject;
static PFN_GetTextMetricsW pGetTextMetricsW;
static PFN_GetGlyphOutlineW pGetGlyphOutlineW;
static PFN_GetKerningPairsW pGetKerningPairsW;
static PFN_GetCharWidth32W pGetCharWidth32W;
static PFN_SetUnhandledExceptionFilter pSetUnhandledExceptionFilter;
static PFN_FlushFileBuffers pFlushFileBuffers;
static void *gPreviousExceptionFilter;
static U32 gExceptionFilterInstalled;
static FontCache *gFonts[FONT_CACHE_SLOTS];
static U32 gInitState;
static void *gSeenDevice;
static U32 gLogState;
static HANDLE gLogHandle;
static U32 gDrawLogged;
static U32 gMeasureLogged;
static U32 gMode1InfoLogged;
static U32 gGlyphLogCount;
static U32 gGlyphStageLogCount;
static U32 gMeasureStageLogCount;
static U32 gD3DStageLogCount;
static U32 gAtlasLogCount;
static U32 gRenderLogCount;
static U32 gMeasureLogCount;
static Mode1FontSize gMode1Fonts[32];
static U32 gMode1FontCursor;
static U32 gFontScalePercent=100u;
static U32 gScalePollCounter;
static U32 gScaleConfigInitialized;

static void log_msg(const char *msg);
static void log_hex(const char *label,U32 value);

#pragma pack(push,4)
typedef struct {
  U32 ExceptionCode, ExceptionFlags;
  void *ExceptionRecord;
  void *ExceptionAddress;
  U32 NumberParameters;
  U32 ExceptionInformation[15];
} EXCEPTION_RECORD32;
typedef struct { EXCEPTION_RECORD32 *ExceptionRecord; void *ContextRecord; } EXCEPTION_POINTERS32;
#pragma pack(pop)

typedef I32 (STDCALL *PFN_OldExceptionFilter)(EXCEPTION_POINTERS32*);
static I32 STDCALL ttf_exception_filter(EXCEPTION_POINTERS32 *ep) {
  I32 result=0;
  if(ep&&ep->ExceptionRecord){
    log_msg("\r\n=== TTF BACKEND UNHANDLED EXCEPTION ===\r\n");
    log_hex("FATAL exception code",ep->ExceptionRecord->ExceptionCode);
    log_hex("FATAL exception address",(U32)(uintptr_t)ep->ExceptionRecord->ExceptionAddress);
    if(ep->ContextRecord){
      const U8 *ctx=(const U8*)ep->ContextRecord;
      log_hex("FATAL context EIP",*(const U32*)(ctx+0xB8));
      log_hex("FATAL context ESP",*(const U32*)(ctx+0xC4));
      log_hex("FATAL context EBP",*(const U32*)(ctx+0xB4));
    }
    if(gLogHandle&&gLogHandle!=INVALID_HANDLE_VALUE&&pFlushFileBuffers)pFlushFileBuffers(gLogHandle);
  }
  if(gPreviousExceptionFilter&&gPreviousExceptionFilter!=(void*)ttf_exception_filter)
    result=((PFN_OldExceptionFilter)gPreviousExceptionFilter)(ep);
  return result;
}
static void install_exception_filter(void) {
  if(gExceptionFilterInstalled)return;
  HMODULE k=IAT(PFN_LoadLibraryA,IAT_LOADLIBRARYA)("kernel32.dll");
  PFN_GetProcAddress gp=IAT(PFN_GetProcAddress,IAT_GETPROCADDRESS);
  if(k&&gp){
    pSetUnhandledExceptionFilter=(PFN_SetUnhandledExceptionFilter)gp(k,"SetUnhandledExceptionFilter");
    pFlushFileBuffers=(PFN_FlushFileBuffers)gp(k,"FlushFileBuffers");
    if(pSetUnhandledExceptionFilter){gPreviousExceptionFilter=(void*)(uintptr_t)pSetUnhandledExceptionFilter((void*)ttf_exception_filter);gExceptionFilterInstalled=1u;}
  }
  log_msg("PHASE: unhandled-exception address logging installed\r\n");
}

static void *heap_alloc(U32 n) {
  if (!gHeap) gHeap = IAT(PFN_GetProcessHeap,IAT_GETPROCESSHEAP)();
  if (!gHeap) return NULL;
  return IAT(PFN_HeapAlloc,IAT_HEAPALLOC)(gHeap,8u,n?n:1u);
}
static void heap_free(void *p) { if (p && gHeap) IAT(PFN_HeapFree,IAT_HEAPFREE)(gHeap,0,p); }
static U32 cstrlen(const char *s) { U32 n=0; if(s) while(s[n]) ++n; return n; }
static void mem_copy(void *d,const void *s,U32 n);
static void log_msg(const char *msg) {
  if(!msg || !gRoot[0] || gLogState==2u) return;
  if(gLogState==0u) {
    char path[520]; U32 n=cstrlen(gRoot),m=0;
    if(n+24u>=sizeof(path)){gLogState=2u;return;}
    mem_copy(path,gRoot,n); m=n;
    mem_copy(path+m,"SLRR_TTF_runtime.log",20u); m+=20u; path[m]=0;
    gLogHandle=IAT(PFN_CreateFileA,IAT_CREATEFILEA)(path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(gLogHandle==INVALID_HANDLE_VALUE){gLogState=2u;return;}
    gLogState=1u;
  }
  if(gLogState==1u){DWORD wrote=0;IAT(PFN_WriteFile,IAT_WRITEFILE)(gLogHandle,msg,cstrlen(msg),&wrote,NULL);}
}
static void log_hex(const char *label,U32 value) {
  static const char hex[]="0123456789ABCDEF";char line[160];U32 n=0;
  while(label&&label[n]&&n<100u){line[n]=label[n];n++;}
  line[n++]='=';line[n++]='0';line[n++]='x';
  for(I32 sh=28;sh>=0;sh-=4)line[n++]=hex[(value>>(U32)sh)&15u];
  line[n++]='\r';line[n++]='\n';line[n]=0;log_msg(line);
}
static void log_dec(const char *label,U32 value) {
  char line[160],digits[16];U32 n=0,d=0;
  while(label&&label[n]&&n<100u){line[n]=label[n];n++;}
  line[n++]='=';do{digits[d++]=(char)('0'+value%10u);value/=10u;}while(value&&d<15u);
  while(d)line[n++]=digits[--d];line[n++]='\r';line[n++]='\n';line[n]=0;log_msg(line);
}
static void log_bytes(const char *label,const char *value,U32 max_bytes) {
  static const char hex[]="0123456789ABCDEF";char line[256];U32 n=0,i=0;
  while(label&&label[i]&&n<80u)line[n++]=label[i++];line[n++]='=';
  if(value){for(i=0;i<max_bytes&&value[i]&&n+3u<sizeof(line);i++){U8 c=(U8)value[i];line[n++]=hex[c>>4];line[n++]=hex[c&15u];line[n++]=' ';}}
  line[n++]='\r';line[n++]='\n';line[n]=0;log_msg(line);
}
static U32 hex_digit(U8 c) { if(c>='0'&&c<='9')return c-'0';if(c>='a'&&c<='f')return c-'a'+10u;if(c>='A'&&c<='F')return c-'A'+10u;return 0xffffffffu; }
static U32 parse_hex8(const U8 *p,U32 *valid) { U32 v=0;*valid=1u;for(U32 i=0;i<8u;i++){U32 d=hex_digit(p[i]);if(d==0xffffffffu){*valid=0u;return 0u;}v=(v<<4)|d;}return v; }
static void mem_zero(void *p,U32 n) { U8 *b=(U8*)p; while(n--) *b++=0; }
static void mem_copy(void *d,const void *s,U32 n) { U8 *a=(U8*)d; const U8 *b=(const U8*)s; while(n--) *a++=*b++; }
static U32 rd16be(const U8 *p) { return ((U32)p[0]<<8)|p[1]; }
static U32 rd32be(const U8 *p) { return ((U32)p[0]<<24)|((U32)p[1]<<16)|((U32)p[2]<<8)|p[3]; }
static U32 align4(U32 n) { return (n+3u)&~3u; }

static void init_root(void) {
  if(gRoot[0])return;
  char p[260]; U32 n=IAT(PFN_GetModuleFileNameA,IAT_GETMODULEFILENAMEA)(NULL,p,259u);
  if(!n||n>=259u) return;
  while(n && p[n-1]!='\\' && p[n-1]!='/') --n;
  if(n>=sizeof(gRoot)) n=sizeof(gRoot)-1u;
  mem_copy(gRoot,p,n); gRoot[n]=0;
  install_exception_filter();
  log_msg("PHASE: executable directory resolved\r\n");
}
static BOOL read_ttf(U8 **out,U32 *out_size) {
  char path[520]; U32 n=cstrlen(gRoot),i=0;
  if(n+15u>=sizeof(path)) return FALSE;
  mem_copy(path,gRoot,n); i=n;
  if(i && path[i-1]!='\\' && path[i-1]!='/') path[i++]='\\';
  mem_copy(path+i,"Fonts\\UI.ttf",12u); i+=12u; path[i]=0;
  HANDLE h=IAT(PFN_CreateFileA,IAT_CREATEFILEA)(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
  if(h==INVALID_HANDLE_VALUE){log_msg("ERROR: CreateFileA Fonts\\UI.ttf failed\r\n");return FALSE;}
  log_msg("PHASE: Fonts\\UI.ttf opened\r\n");
  U32 sz=IAT(PFN_GetFileSize,IAT_GETFILESIZE)(h,NULL);
  log_dec("TTF file bytes",sz);
  if(sz<12u||sz>0x04000000u){IAT(PFN_CloseHandle,IAT_CLOSEHANDLE)(h);return FALSE;}
  U8 *buf=(U8*)heap_alloc(sz); if(!buf){IAT(PFN_CloseHandle,IAT_CLOSEHANDLE)(h);return FALSE;}
  DWORD got=0; BOOL ok=IAT(PFN_ReadFile,IAT_READFILE)(h,buf,sz,&got,NULL);
  IAT(PFN_CloseHandle,IAT_CLOSEHANDLE)(h);
  if(!ok||got!=sz){heap_free(buf);return FALSE;}
  *out=buf; *out_size=sz; return TRUE;
}
static U32 read_font_scale_percent(void) {
  char path[520],buf[16]; U32 n=cstrlen(gRoot),i=0,sz=0; DWORD got=0;
  if(n+17u>=sizeof(path))return 100u;
  mem_copy(path,gRoot,n); i=n;
  if(i&&path[i-1]!='\\'&&path[i-1]!='/')path[i++]='\\';
  mem_copy(path+i,"Fonts\\TTF.scale",15u); i+=15u; path[i]=0;
  HANDLE h=IAT(PFN_CreateFileA,IAT_CREATEFILEA)(path,GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
  if(h==INVALID_HANDLE_VALUE)return 0xffffffffu;
  sz=IAT(PFN_GetFileSize,IAT_GETFILESIZE)(h,NULL);
  if(sz==0u||sz==0xffffffffu||sz>sizeof(buf)){IAT(PFN_CloseHandle,IAT_CLOSEHANDLE)(h);return 0xffffffffu;}
  BOOL ok=IAT(PFN_ReadFile,IAT_READFILE)(h,buf,sz,&got,NULL);
  IAT(PFN_CloseHandle,IAT_CLOSEHANDLE)(h);
  if(!ok||got==0u)return 0xffffffffu;
  U32 value=0u,digits=0u;
  for(U32 k=0;k<got;k++){
    U8 c=(U8)buf[k];
    if(c>='0'&&c<='9'){value=value*10u+(U32)(c-'0');digits++;if(digits>3u)break;}
    else if(digits)break;
    else if(c==' '||c=='\t'||c=='\r'||c=='\n')continue;
    else return 0xffffffffu;
  }
  if(!digits)return 0xffffffffu;
  if(value<60u)value=60u;if(value>150u)value=150u;
  return value;
}
static void refresh_font_scale(void) {
  if(!gScaleConfigInitialized){
    U32 value=read_font_scale_percent();
    if(value!=0xffffffffu)gFontScalePercent=value;
    gScaleConfigInitialized=1u;
    log_dec("TTF font scale percent loaded",gFontScalePercent);
    return;
  }
  /* Poll occasionally; the Java options slider writes Fonts\\TTF.scale. */
  if(++gScalePollCounter<512u)return;
  gScalePollCounter=0u;
  U32 value=read_font_scale_percent();
  if(value!=0xffffffffu&&value!=gFontScalePercent){gFontScalePercent=value;log_dec("TTF font scale percent changed",gFontScalePercent);}
}

static BOOL ttf_family(const U8 *f,U32 fs,U16 *out,U32 cap) {
  U32 nt,name_off=0,name_len=0,i,cnt,str_off,best_idx=0,best_len=0; I32 best=-1;
  if(fs<12u) return FALSE; nt=rd16be(f+4); if(nt>256u) return FALSE;
  for(i=0;i<nt;i++){U32 o=12u+i*16u;if(o+16u>fs)break;if(f[o]=='n'&&f[o+1]=='a'&&f[o+2]=='m'&&f[o+3]=='e'){name_off=rd32be(f+o+8);name_len=rd32be(f+o+12);break;}}
  if(!name_off||name_off+6u>fs||name_len>fs-name_off)return FALSE;
  const U8 *n=f+name_off;cnt=rd16be(n+2);str_off=rd16be(n+4);if(cnt>1024u||str_off>name_len)return FALSE;
  for(i=0;i<cnt;i++){U32 o=6u+i*12u;U32 platform,encoding,lang,id,len,off;I32 score;if(o+12u>name_len)break;
    platform=rd16be(n+o);encoding=rd16be(n+o+2);lang=rd16be(n+o+4);id=rd16be(n+o+6);len=rd16be(n+o+8);off=rd16be(n+o+10);
    if((id!=16u&&id!=1u)||len<2u||(platform!=3u&&platform!=0u)||str_off+off+len>name_len)continue;
    score=(id==16u?100:0)+(platform==3u?20:0)+(lang==0x0409u?10:0)+(encoding==1u?5:0);
    if(score>best){best=score;best_idx=str_off+off;best_len=len;}
  }
  if(best<0)return FALSE; U32 chars=best_len/2u;if(chars>=cap)chars=cap-1u;
  for(i=0;i<chars;i++)out[i]=(U16)rd16be(n+best_idx+i*2u);out[chars]=0;return chars!=0;
}
static BOOL load_gdi(U8 *ttf,U32 ttf_size) {
  HMODULE gdi=IAT(PFN_LoadLibraryA,IAT_LOADLIBRARYA)("gdi32.dll");
  PFN_GetProcAddress gp=IAT(PFN_GetProcAddress,IAT_GETPROCADDRESS);
  if(!gdi||!gp)return FALSE;
  log_msg("PHASE: gdi32.dll loaded\r\n");
  pAddFontResourceExW=(PFN_AddFontResourceExW)gp(gdi,"AddFontResourceExW");
  pCreateCompatibleDC=(PFN_CreateCompatibleDC)gp(gdi,"CreateCompatibleDC");
  pDeleteDC=(PFN_DeleteDC)gp(gdi,"DeleteDC");
  pCreateFontIndirectW=(PFN_CreateFontIndirectW)gp(gdi,"CreateFontIndirectW");
  pSelectObject=(PFN_SelectObject)gp(gdi,"SelectObject");
  pDeleteObject=(PFN_DeleteObject)gp(gdi,"DeleteObject");
  pGetTextMetricsW=(PFN_GetTextMetricsW)gp(gdi,"GetTextMetricsW");
  pGetGlyphOutlineW=(PFN_GetGlyphOutlineW)gp(gdi,"GetGlyphOutlineW");
  pGetKerningPairsW=(PFN_GetKerningPairsW)gp(gdi,"GetKerningPairsW");
  pGetCharWidth32W=(PFN_GetCharWidth32W)gp(gdi,"GetCharWidth32W");
  if(!pAddFontResourceExW||!pCreateCompatibleDC||!pDeleteDC||!pCreateFontIndirectW||!pSelectObject||!pDeleteObject||!pGetTextMetricsW||!pGetGlyphOutlineW){log_msg("ERROR: one or more GDI exports are missing\r\n");return FALSE;}
  log_msg("PHASE: required GDI exports resolved\r\n");
  if(!ttf_family(ttf,ttf_size,gFace,32u)){log_msg("ERROR: TTF name table parse failed\r\n");return FALSE;}
  log_msg("PHASE: TTF family name parsed\r\n");
  char path[520]; U32 n=cstrlen(gRoot),i=n;
  if(n+15u>=sizeof(path))return FALSE;
  mem_copy(path,gRoot,n); if(i&&path[i-1]!='\\'&&path[i-1]!='/')path[i++]='\\';
  mem_copy(path+i,"Fonts\\UI.ttf",12u);i+=12u;path[i]=0;
  if(IAT(PFN_MultiByteToWideChar,IAT_MULTIBYTETOWIDECHAR)(0,0,path,-1,gTtfPath,520u)==0)return FALSE;
  U32 added=pAddFontResourceExW(gTtfPath,FR_PRIVATE,NULL);log_dec("GDI AddFontResourceExW result",added);
  return added!=0;
}
static BOOL init_ttf(void) {
  U8 *ttf=NULL; U32 size=0; BOOL ok;
  if(gInitState==2u)return TRUE; if(gInitState==3u)return FALSE;
  if(gInitState==1u)return FALSE; gInitState=1u;
  init_root(); if(!gRoot[0]){gInitState=3u;return FALSE;}
  log_msg("SLRR runtime TTF backend entered\r\n");
  if(!read_ttf(&ttf,&size)){log_msg("ERROR: Fonts\\UI.ttf could not be read\r\n");gInitState=3u;return FALSE;}
  log_msg("OK: Fonts\\UI.ttf read\r\n");
  ok=load_gdi(ttf,size); heap_free(ttf);
  log_msg(ok?"OK: GDI private font registered\r\n":"ERROR: GDI font registration failed\r\n");
  gInitState=ok?2u:3u;
  log_msg(ok?"PHASE: TTF initialization complete\r\n":"PHASE: TTF initialization FAILED\r\n");
  return ok;
}

static U32 hash_code(U32 c) { c^=c>>16; c*=0x7feb352du; c^=c>>15; c*=0x846ca68bu; c^=c>>16; return c&(GLYPH_HASH_SIZE-1u); }
static U32 hash_pair(U32 a,U32 b) { U32 x=(a*0x9e3779b1u)^(b+0x85ebca6bu);x^=x>>16;return x&(KERN_HASH_SIZE-1u); }
static FontCache *font_cache(U32 px) {
  FontCache *fc; LOGFONTW lf; TEXTMETRICW tm;
  if(px<1u)px=1u;if(px>128u)px=128u;
  if(gFonts[px])return gFonts[px]; if(!init_ttf())return NULL;
  fc=(FontCache*)heap_alloc(sizeof(FontCache)); if(!fc)return NULL;mem_zero(fc,sizeof(*fc));
  fc->px=px;fc->glyphs=(GlyphRec*)heap_alloc(sizeof(GlyphRec)*GLYPH_HASH_SIZE);
  fc->kern=(KernRec*)heap_alloc(sizeof(KernRec)*KERN_HASH_SIZE);
  if(!fc->glyphs||!fc->kern){heap_free(fc->glyphs);heap_free(fc->kern);heap_free(fc);return NULL;}
  mem_zero(fc->glyphs,sizeof(GlyphRec)*GLYPH_HASH_SIZE);mem_zero(fc->kern,sizeof(KernRec)*KERN_HASH_SIZE);
  fc->dc=pCreateCompatibleDC(NULL);if(!fc->dc){heap_free(fc->glyphs);heap_free(fc->kern);heap_free(fc);return NULL;}
  mem_zero(&lf,sizeof(lf));lf.lfHeight=-(I32)px;lf.lfWeight=FW_NORMAL;lf.lfCharSet=DEFAULT_CHARSET;
  lf.lfOutPrecision=OUT_DEFAULT_PRECIS;lf.lfClipPrecision=CLIP_DEFAULT_PRECIS;lf.lfQuality=ANTIALIASED_QUALITY;lf.lfPitchAndFamily=DEFAULT_PITCH|FF_DONTCARE;
  for(U32 i=0;i<32u;i++)lf.lfFaceName[i]=gFace[i];
  fc->font=pCreateFontIndirectW(&lf);if(!fc->font){pDeleteDC(fc->dc);heap_free(fc->glyphs);heap_free(fc->kern);heap_free(fc);return NULL;}
  fc->old_font=pSelectObject(fc->dc,fc->font);
  if(!fc->old_font||(intptr_t)fc->old_font==-1||!pGetTextMetricsW(fc->dc,&tm)){pDeleteDC(fc->dc);if(fc->font)pDeleteObject(fc->font);heap_free(fc->glyphs);heap_free(fc->kern);heap_free(fc);return NULL;}
  fc->ascent=tm.tmAscent;fc->descent=tm.tmDescent;fc->line_height=tm.tmHeight+tm.tmExternalLeading;
  if(fc->line_height<=0)fc->line_height=(I32)px;
  log_dec("PHASE: GDI font cache created px",px);log_dec("Font ascent",(U32)fc->ascent);log_dec("Font descent",(U32)fc->descent);log_dec("Font line height",(U32)fc->line_height);
  fc->pen_x=1u;fc->pen_y=1u;fc->row_h=0;fc->ready=1u;
  if(pGetKerningPairsW){DWORD count=pGetKerningPairsW(fc->dc,0,NULL);if(count>0u&&count<8192u){KERNINGPAIR *kp=(KERNINGPAIR*)heap_alloc(count*sizeof(KERNINGPAIR));if(kp){DWORD got=pGetKerningPairsW(fc->dc,count,kp);U32 inserted=0;for(U32 i=0;i<got&&inserted<KERN_HASH_SIZE-1u;i++){U32 key=((U32)kp[i].wFirst<<16)|kp[i].wSecond;U32 h=hash_pair(kp[i].wFirst,kp[i].wSecond),probe=0;while(fc->kern[h].used&&probe++<KERN_HASH_SIZE)h=(h+1u)&(KERN_HASH_SIZE-1u);if(probe>=KERN_HASH_SIZE)break;fc->kern[h].key=key;fc->kern[h].amount=(I16)kp[i].iKernAmount;fc->kern[h].used=1u;inserted++;}heap_free(kp);}}}
  gFonts[px]=fc;return fc;
}
static I32 kerning(FontCache *fc,U32 a,U32 b) {
  U32 key=(a<<16)|(b&0xffffu),h=hash_pair(a,b),start=h;
  if(!fc||!a||!b)return 0;
  while(fc->kern[h].used){if(fc->kern[h].key==key)return fc->kern[h].amount;h=(h+1u)&(KERN_HASH_SIZE-1u);if(h==start)break;}
  return 0;
}
static void *d3d_device(void) { return *(void * volatile *)D3D_DEVICE_PTR; }
static void release_device_cache(void) {
  void *dev=d3d_device(); if(dev==gSeenDevice)return;
  log_hex("PHASE: D3D device pointer changed",(U32)(uintptr_t)dev);
  gSeenDevice=dev;
  for(U32 i=1;i<FONT_CACHE_SLOTS;i++)if(gFonts[i]){
    FontCache *fc=gFonts[i]; fc->texture=NULL;
    if(fc->glyphs)mem_zero(fc->glyphs,sizeof(GlyphRec)*GLYPH_HASH_SIZE);
    fc->glyph_count=0;fc->pen_x=1u;fc->pen_y=1u;fc->row_h=0;
  }
}
static BOOL create_atlas(FontCache *fc) {
  void *dev=d3d_device();void **vt;PFN_D3DCreateTexture create;
  if(!dev||!fc){log_msg("ERROR: D3D device/cache unavailable for atlas\r\n");return FALSE;}vt=*(void***)dev;create=(PFN_D3DCreateTexture)vt[23];
  if(!create){log_msg("ERROR: IDirect3DDevice9::CreateTexture slot missing\r\n");return FALSE;}
  if(gAtlasLogCount++<4u){log_msg("PHASE: creating 1024x1024 glyph atlas\r\n");log_hex("D3D device",(U32)(uintptr_t)dev);}
  HRESULT cr=create(dev,ATLAS_SIZE,ATLAS_SIZE,1u,0u,D3DFMT_A8R8G8B8,D3DPOOL_MANAGED,&fc->texture,NULL);
  if(cr<0){log_hex("ERROR: CreateTexture HRESULT",(U32)cr);return FALSE;}
  PFN_TexLockRect lock;PFN_TexUnlockRect unlock;void **tv=*(void***)fc->texture;
  lock=(PFN_TexLockRect)tv[19];unlock=(PFN_TexUnlockRect)tv[20];
  D3DLOCKED_RECT lr; HRESULT lrhr=lock(fc->texture,0,&lr,NULL,0u);if(lrhr<0){log_hex("ERROR: atlas LockRect HRESULT",(U32)lrhr);PFN_Release release=(PFN_Release)(*(void***)fc->texture)[2];release(fc->texture);fc->texture=NULL;return FALSE;}
  for(U32 y=0;y<ATLAS_SIZE;y++){U32 *row=(U32*)((U8*)lr.pBits+y*(U32)lr.Pitch);for(U32 x=0;x<ATLAS_SIZE;x++)row[x]=0u;}
  unlock(fc->texture,0);log_msg("PHASE: glyph atlas created and initialized\r\n");return TRUE;
}
static void reset_atlas(FontCache *fc) {
  if(!fc)return;mem_zero(fc->glyphs,sizeof(GlyphRec)*GLYPH_HASH_SIZE);fc->glyph_count=0;fc->pen_x=1;fc->pen_y=1;fc->row_h=0;
  if(fc->texture){void **tv=*(void***)fc->texture;PFN_TexLockRect lock=(PFN_TexLockRect)tv[19];PFN_TexUnlockRect unlock=(PFN_TexUnlockRect)tv[20];D3DLOCKED_RECT lr;if(lock(fc->texture,0,&lr,NULL,0u)>=0){for(U32 y=0;y<ATLAS_SIZE;y++){U32 *row=(U32*)((U8*)lr.pBits+y*(U32)lr.Pitch);for(U32 x=0;x<ATLAS_SIZE;x++)row[x]=0u;}unlock(fc->texture,0);}}
}
static U32 cp1251(U8 c) {
  static const U16 special[64]={0x0402,0x0403,0x201a,0x0453,0x201e,0x2026,0x2020,0x2021,0x20ac,0x2030,0x0409,0x2039,0x040a,0x040c,0x040b,0x040f,0x0452,0x2018,0x2019,0x201c,0x201d,0x2022,0x2013,0x2014,0x0000,0x2122,0x0459,0x203a,0x045a,0x045c,0x045b,0x045f,0x00a0,0x040e,0x045e,0x0408,0x00a4,0x0490,0x00a6,0x00a7,0x0401,0x00a9,0x0404,0x00ab,0x00ac,0x00ad,0x00ae,0x0407,0x00b0,0x00b1,0x0406,0x0456,0x0491,0x00b5,0x00b6,0x00b7,0x0451,0x2116,0x0454,0x00bb,0x0458,0x0405,0x0455};
  if(c<0x80u)return c;if(c>=0xc0u)return 0x0410u+(U32)(c-0xc0u);if(c==0xa8u)return 0x0401u;if(c==0xb8u)return 0x0451u;if(c>=0x80u&&c<=0xbfu)return special[c-0x80u];return c;
}
static U32 next_cp(const U8 **pp) {
  const U8 *p=*pp;U32 c=p[0];if(!c)return 0;
  if(c<0x80u){*pp=p+1;return c;}
  if(c>=0xc2u&&c<=0xdfu&&p[1]>=0x80u&&p[1]<=0xbfu){*pp=p+2;return ((c&0x1fu)<<6)|(p[1]&0x3fu);}
  if(c>=0xe0u&&c<=0xefu&&p[1]>=0x80u&&p[1]<=0xbfu&&p[2]>=0x80u&&p[2]<=0xbfu){U32 x=((c&0x0fu)<<12)|((p[1]&0x3fu)<<6)|(p[2]&0x3fu);if(x>=0x800u&&(x<0xd800u||x>0xdfffu)){*pp=p+3;return x;}}
  if(c>=0xf0u&&c<=0xf4u&&p[1]>=0x80u&&p[1]<=0xbfu&&p[2]>=0x80u&&p[2]<=0xbfu&&p[3]>=0x80u&&p[3]<=0xbfu){*pp=p+4;return '?';}
  *pp=p+1;return cp1251((U8)c);
}
static GlyphRec *find_glyph(FontCache *fc,U32 cp) {
  U32 h=hash_code(cp),start=h;GlyphRec *g;
  while(fc->glyphs[h].used){if(fc->glyphs[h].codepoint==cp)return &fc->glyphs[h];h=(h+1u)&(GLYPH_HASH_SIZE-1u);if(h==start)return NULL;}
  return NULL;
}
static GlyphRec *get_glyph(FontCache *fc,U32 cp) {
  if(!fc)return NULL;
  if(cp>0xffffu||(cp>=0xd800u&&cp<=0xdfffu))cp='?';
  GlyphRec *old=find_glyph(fc,cp);if(old)return old;
  MAT2 mat;GLYPHMETRICS gm;DWORD need=0,got=0;U32 w=0,h=0,pitch=0;U8 *bits=NULL;
  mem_zero(&gm,sizeof(gm));
  mat.eM11.value=1;mat.eM11.fract=0;mat.eM12.value=0;mat.eM12.fract=0;mat.eM21.value=0;mat.eM21.fract=0;mat.eM22.value=1;mat.eM22.fract=0;
  if(gGlyphStageLogCount<24u){log_hex("GLYPH stage: GetGlyphOutline entry codepoint",cp);log_hex("GLYPH stage: selected HDC",(U32)(uintptr_t)fc->dc);gGlyphStageLogCount++;}
  /* Whitespace has no bitmap. Avoid issuing a GDI outline request for it. */
  if(cp==' '||cp=='\t'){
    I32 cw=0;
    if(pGetCharWidth32W&&pGetCharWidth32W(fc->dc,cp,cp,&cw))gm.gmCellIncX=(I16)cw;
    else gm.gmCellIncX=(I16)(fc->px/3u);
    w=0;h=0;need=0;
    log_msg("GLYPH stage: whitespace metrics path (no bitmap request)\r\n");
  } else {
    need=pGetGlyphOutlineW(fc->dc,cp,GGO_GRAY8_BITMAP,&gm,0,NULL,&mat);
    if(gGlyphStageLogCount<28u){log_hex("GLYPH stage: outline query result",need);log_dec("GLYPH stage: black box width",(U32)gm.gmBlackBoxX);log_dec("GLYPH stage: black box height",(U32)gm.gmBlackBoxY);gGlyphStageLogCount++;}
    if(need==GDI_ERROR&&cp!='?'){
      cp='?';mem_zero(&gm,sizeof(gm));
      need=pGetGlyphOutlineW(fc->dc,cp,GGO_GRAY8_BITMAP,&gm,0,NULL,&mat);
      log_hex("GLYPH stage: fallback question-mark query",need);
    }
    if(need==GDI_ERROR){log_msg("GLYPH stage: GDI returned GDI_ERROR\r\n");return NULL;}
    w=(U32)(gm.gmBlackBoxX>0?gm.gmBlackBoxX:0);h=(U32)(gm.gmBlackBoxY>0?gm.gmBlackBoxY:0);pitch=align4(w);
    if(w&&h&&((U64)pitch*(U64)h>(U64)need)){log_dec("GLYPH stage: invalid bitmap allocation size",need);log_dec("GLYPH stage: required bitmap bytes",pitch*h);return NULL;}
    if(need){
      bits=(U8*)heap_alloc(need);if(!bits){log_msg("GLYPH stage: bitmap allocation failed\r\n");return NULL;}
      log_dec("GLYPH stage: bitmap buffer bytes",need);
      got=pGetGlyphOutlineW(fc->dc,cp,GGO_GRAY8_BITMAP,&gm,need,bits,&mat);
      if(got==GDI_ERROR){log_msg("GLYPH stage: bitmap fetch returned GDI_ERROR\r\n");heap_free(bits);return NULL;}
      if(got>need){log_msg("GLYPH stage: GDI bitmap byte count exceeds allocation\r\n");heap_free(bits);return NULL;}
      log_dec("GLYPH stage: bitmap fetch bytes",got);
    }
  }
  if(w&&h&&d3d_device()){
    if(!fc->texture&&!create_atlas(fc)){log_msg("GLYPH stage: atlas create failed\r\n");heap_free(bits);return NULL;}
    if(fc->pen_x+w+2u>=ATLAS_SIZE){fc->pen_x=1u;fc->pen_y+=fc->row_h+2u;fc->row_h=0;}
    if(fc->pen_y+h+2u>=ATLAS_SIZE){reset_atlas(fc);}
  }
  U32 slot=hash_code(cp),start=slot;while(fc->glyphs[slot].used){slot=(slot+1u)&(GLYPH_HASH_SIZE-1u);if(slot==start){reset_atlas(fc);slot=hash_code(cp);break;}}
  GlyphRec *g=&fc->glyphs[slot];mem_zero(g,sizeof(*g));g->used=1;g->codepoint=cp;g->w=(U16)w;g->h=(U16)h;g->bearing_x=(I16)gm.gmptGlyphOrigin.x;g->bearing_y=(I16)gm.gmptGlyphOrigin.y;g->advance=gm.gmCellIncX;
  if(w&&h){
    U32 ax=fc->pen_x,ay=fc->pen_y;RECT r={(I32)ax,(I32)ay,(I32)(ax+w),(I32)(ay+h)};
    if(!fc->texture){heap_free(bits);g->used=0;log_msg("GLYPH stage: missing atlas before glyph upload\r\n");return NULL;}
    void **tv=*(void***)fc->texture;PFN_TexLockRect lock=(PFN_TexLockRect)tv[19];PFN_TexUnlockRect unlock=(PFN_TexUnlockRect)tv[20];D3DLOCKED_RECT lr;
    if(gGlyphStageLogCount<32u){log_msg("GLYPH stage: before atlas LockRect\r\n");gGlyphStageLogCount++;}
    HRESULT lockhr=lock(fc->texture,0,&lr,&r,0u);
    if(lockhr<0){log_hex("GLYPH stage: atlas LockRect HRESULT",(U32)lockhr);heap_free(bits);g->used=0;return NULL;}
    if(!lr.pBits||lr.Pitch<=0){log_msg("GLYPH stage: atlas LockRect returned invalid memory\r\n");unlock(fc->texture,0);heap_free(bits);g->used=0;return NULL;}
    for(U32 yy=0;yy<h;yy++){const U8 *src=bits+yy*pitch;U32 *dst=(U32*)((U8*)lr.pBits+yy*(U32)lr.Pitch);for(U32 xx=0;xx<w;xx++){U32 a=((U32)src[xx]*255u+32u)/64u;dst[xx]=(a<<24)|0x00ffffffu;}}
    unlock(fc->texture,0);g->x=(U16)ax;g->y=(U16)ay;fc->pen_x+=w+2u;if(h>fc->row_h)fc->row_h=h;
  }
  heap_free(bits);fc->glyph_count++;
  if(gGlyphLogCount<16u){log_hex("GLYPH codepoint",cp);log_dec("Glyph width",w);log_dec("Glyph height",h);log_dec("Glyph advance",(U32)(I32)g->advance);gGlyphLogCount++;}
  return g;
}

static float measure(FontCache *fc,const char *text,float scale) {
  const U8 *p=(const U8*)text;float x=0.f,maxw=0.f;U32 prev=0,cp,count=0;
  if(!fc||!text)return 0.f;
  if(gMeasureStageLogCount<8u){log_msg("PHASE: measure() entered\r\n");log_hex("measure text pointer",(U32)(uintptr_t)text);log_hex("measure scale x1000",(U32)(scale*1000.f));gMeasureStageLogCount++;}
  while(*p&&count++<MAX_TEXT_CHARS){
    if(p[0]==0x1bu){if(p[1]=='C')p+=10u;else if(p[1])p+=2u;else p++;prev=0;continue;}
    cp=next_cp(&p);if(!cp)break;if(cp=='\r')continue;
    if(cp=='\n'){if(x>maxw)maxw=x;x=0.f;prev=0;continue;}
    if(cp=='\t'){GlyphRec *sp=get_glyph(fc,' ');x+=(float)(sp?sp->advance:(I32)(fc->px/3u))*4.f*scale;prev=0;continue;}
    if(prev)x+=(float)kerning(fc,prev,cp)*scale;
    GlyphRec *g=get_glyph(fc,cp);if(g)x+=(float)g->advance*scale;prev=cp;
  }
  if(x>maxw)maxw=x;
  if(gMeasureStageLogCount<=8u)log_dec("measure completed width x100",(U32)(maxw*100.f));
  return maxw;
}

static U32 gPrimitiveErrorLogged;
static void draw_glyph(FontCache *fc,void *dev,float x,float baseline_y,float scale,DWORD color,GlyphRec *g) {
  if(!g||!g->w||!g->h||!fc->texture)return;
  void **vt=*(void***)dev;PFN_D3DDrawUP draw=(PFN_D3DDrawUP)vt[83];
  float left=x+(float)g->bearing_x*scale;float top=baseline_y-(float)g->bearing_y*scale;
  float right=left+(float)g->w*scale;float bottom=top+(float)g->h*scale;
  float u0=(float)g->x/(float)ATLAS_SIZE,v0=(float)g->y/(float)ATLAS_SIZE;
  float u1=(float)(g->x+g->w)/(float)ATLAS_SIZE,v1=(float)(g->y+g->h)/(float)ATLAS_SIZE;
  TTFVertex v[4];v[0].x=left;v[0].y=top;v[0].z=0.f;v[0].rhw=1.f;v[0].color=color;v[0].u=u0;v[0].v=v0;
  v[1].x=right;v[1].y=top;v[1].z=0.f;v[1].rhw=1.f;v[1].color=color;v[1].u=u1;v[1].v=v0;
  v[2].x=left;v[2].y=bottom;v[2].z=0.f;v[2].rhw=1.f;v[2].color=color;v[2].u=u0;v[2].v=v1;
  v[3].x=right;v[3].y=bottom;v[3].z=0.f;v[3].rhw=1.f;v[3].color=color;v[3].u=u1;v[3].v=v1;
  static U32 drawStageCount=0;
  if(drawStageCount<12u){log_hex("DRAW stage: before DrawPrimitiveUP glyph",g->codepoint);drawStageCount++;}
  HRESULT hr=draw(dev,D3DPT_TRIANGLESTRIP,2u,v,sizeof(TTFVertex));
  if(hr<0&&!gPrimitiveErrorLogged){log_hex("ERROR: DrawPrimitiveUP HRESULT",(U32)hr);gPrimitiveErrorLogged=1u;}
  else if(drawStageCount<=12u)log_hex("DRAW stage: DrawPrimitiveUP HRESULT",(U32)hr);
}
static void render_text(FontCache *fc,const char *text,float x,float y,float scale,DWORD color) {
  void *dev=d3d_device();void **vt;
  PFN_D3DGetTexture gettex;PFN_D3DSetTexture settex;
  PFN_D3DGetRenderState getrs;PFN_D3DSetRenderState setrs;
  PFN_D3DGetTSS gettss;PFN_D3DSetTSS settss;
  PFN_D3DGetSampler getsamp;PFN_D3DSetSampler setsamp;
  PFN_D3DGetFVF getfvf;PFN_D3DSetFVF setfvf;
  PFN_D3DGetObject getdecl,getvs,getps;PFN_D3DSetObject setdecl,setvs,setps;
  PFN_D3DGetStreamSource getstream;PFN_D3DSetStreamSource setstream;
  void *oldtex=NULL,*olddecl=NULL,*oldvs=NULL,*oldps=NULL,*oldstream=NULL;
  DWORD oldfvf=0,rsvals[8]={0},tssvals[6]={0},sampmin=0,sampmag=0;
  UINT oldstreamoffset=0,oldstreamstride=0;
  DWORD rsids[8]={D3DRS_ZENABLE,D3DRS_ZWRITEENABLE,D3DRS_LIGHTING,D3DRS_CULLMODE,D3DRS_ALPHATESTENABLE,D3DRS_ALPHABLENDENABLE,D3DRS_SRCBLEND,D3DRS_DESTBLEND};
  DWORD tssids[6]={D3DTSS_COLOROP,D3DTSS_COLORARG1,D3DTSS_COLORARG2,D3DTSS_ALPHAOP,D3DTSS_ALPHAARG1,D3DTSS_ALPHAARG2};
  const UINT SAMP_MAGFILTER=5u,SAMP_MINFILTER=6u;
  U32 have_tex=0,have_decl=0,have_vs=0,have_ps=0,have_stream=0,have_fvf=0;
  U32 states_captured=0,changed=0;
  if(!dev||!fc||!fc->texture||!text){log_msg("ERROR: render_text prerequisites missing\r\n");return;}
  vt=*(void***)dev;
  gettex=(PFN_D3DGetTexture)vt[64];settex=(PFN_D3DSetTexture)vt[65];
  getrs=(PFN_D3DGetRenderState)vt[58];setrs=(PFN_D3DSetRenderState)vt[57];
  gettss=(PFN_D3DGetTSS)vt[66];settss=(PFN_D3DSetTSS)vt[67];
  getsamp=(PFN_D3DGetSampler)vt[68];setsamp=(PFN_D3DSetSampler)vt[69];
  setdecl=(PFN_D3DSetObject)vt[87];getdecl=(PFN_D3DGetObject)vt[88];
  setfvf=(PFN_D3DSetFVF)vt[89];getfvf=(PFN_D3DGetFVF)vt[90];
  setvs=(PFN_D3DSetObject)vt[92];getvs=(PFN_D3DGetObject)vt[93];
  getstream=(PFN_D3DGetStreamSource)vt[101];setstream=(PFN_D3DSetStreamSource)vt[100];
  setps=(PFN_D3DSetObject)vt[107];getps=(PFN_D3DGetObject)vt[108];
  if(!gettex||!settex||!getrs||!setrs||!gettss||!settss||!getsamp||!setsamp||!getfvf||!setfvf||!getdecl||!setdecl||!getvs||!setvs||!getps||!setps||!getstream||!setstream){
    log_msg("ERROR: required D3D9 state vtable entry missing\r\n");return;
  }
  if(gD3DStageLogCount++<4u){log_msg("PHASE: D3D render entry; state capture starting\r\n");log_hex("D3D device vtable",(U32)(uintptr_t)vt);}
  HRESULT hr=gettex(dev,0,&oldtex);if(hr<0){log_hex("D3D GetTexture failed",(U32)hr);goto cleanup; }have_tex=1;
  hr=getfvf(dev,&oldfvf);if(hr<0){log_hex("D3D GetFVF failed",(U32)hr);goto cleanup;}have_fvf=1;
  hr=getdecl(dev,&olddecl);if(hr<0){log_hex("D3D GetVertexDeclaration failed",(U32)hr);goto cleanup;}have_decl=1;
  hr=getvs(dev,&oldvs);if(hr<0){log_hex("D3D GetVertexShader failed",(U32)hr);goto cleanup;}have_vs=1;
  hr=getps(dev,&oldps);if(hr<0){log_hex("D3D GetPixelShader failed",(U32)hr);goto cleanup;}have_ps=1;
  hr=getstream(dev,0,&oldstream,&oldstreamoffset,&oldstreamstride);if(hr<0){log_hex("D3D GetStreamSource failed",(U32)hr);goto cleanup;}have_stream=1;
  for(U32 i=0;i<8;i++){hr=getrs(dev,rsids[i],&rsvals[i]);if(hr<0){log_hex("D3D GetRenderState failed",(U32)hr);goto cleanup;}}
  for(U32 i=0;i<6;i++){hr=gettss(dev,0,tssids[i],&tssvals[i]);if(hr<0){log_hex("D3D GetTextureStageState failed",(U32)hr);goto cleanup;}}
  hr=getsamp(dev,0,SAMP_MINFILTER,&sampmin);if(hr<0){log_hex("D3D GetSamplerState MIN failed",(U32)hr);goto cleanup;}
  hr=getsamp(dev,0,SAMP_MAGFILTER,&sampmag);if(hr<0){log_hex("D3D GetSamplerState MAG failed",(U32)hr);goto cleanup;}
  states_captured=1u;
  if(gD3DStageLogCount<=4u){log_msg("PHASE: all D3D state captured\r\n");log_hex("Original FVF",oldfvf);log_hex("Original vertex declaration",(U32)(uintptr_t)olddecl);log_hex("Original vertex shader",(U32)(uintptr_t)oldvs);log_hex("Original pixel shader",(U32)(uintptr_t)oldps);log_hex("Original stream 0",(U32)(uintptr_t)oldstream);log_dec("Original stream 0 stride",oldstreamstride);}

  hr=setvs(dev,NULL);if(hr<0){log_hex("D3D SetVertexShader(NULL) failed",(U32)hr);goto cleanup;}changed=1u;
  hr=setps(dev,NULL);if(hr<0){log_hex("D3D SetPixelShader(NULL) failed",(U32)hr);goto restore;}
  hr=setfvf(dev,D3DFVF_XYZRHW|D3DFVF_DIFFUSE|D3DFVF_TEX1);if(hr<0){log_hex("D3D SetFVF(TTF) failed",(U32)hr);goto restore;}
  hr=settex(dev,0,fc->texture);if(hr<0){log_hex("D3D SetTexture(TTF atlas) failed",(U32)hr);goto restore;}
  setrs(dev,D3DRS_ZENABLE,0);setrs(dev,D3DRS_ZWRITEENABLE,0);setrs(dev,D3DRS_LIGHTING,0);setrs(dev,D3DRS_CULLMODE,D3DCULL_NONE);setrs(dev,D3DRS_ALPHATESTENABLE,0);setrs(dev,D3DRS_ALPHABLENDENABLE,1);setrs(dev,D3DRS_SRCBLEND,D3DBLEND_SRCALPHA);setrs(dev,D3DRS_DESTBLEND,D3DBLEND_INVSRCALPHA);
  settss(dev,0,D3DTSS_COLOROP,D3DTOP_MODULATE);settss(dev,0,D3DTSS_COLORARG1,D3DTA_TEXTURE);settss(dev,0,D3DTSS_COLORARG2,D3DTA_DIFFUSE);settss(dev,0,D3DTSS_ALPHAOP,D3DTOP_MODULATE);settss(dev,0,D3DTSS_ALPHAARG1,D3DTA_TEXTURE);settss(dev,0,D3DTSS_ALPHAARG2,D3DTA_DIFFUSE);
  setsamp(dev,0,SAMP_MINFILTER,D3DTEXF_LINEAR);setsamp(dev,0,SAMP_MAGFILTER,D3DTEXF_LINEAR);
  log_msg("PHASE: TTF D3D state configured; glyph loop starting\r\n");
  {
    const U8 *p=(const U8*)text;U32 prev=0,cp,count=0,drawn=0;
    float pen=x,base_y=y+(float)fc->ascent*scale;DWORD base_color=color,current_color=color;
    while(*p&&count++<MAX_TEXT_CHARS){
      if(p[0]==0x1bu){
        if(p[1]=='C'){U32 valid=0,new_color=parse_hex8(p+2,&valid);if(valid)current_color=new_color;p+=10u;}
        else if(p[1]=='c'){current_color=base_color;p+=2u;}
        else if(p[1])p+=2u;else p++;
        prev=0;continue;
      }
      cp=next_cp(&p);if(!cp)break;if(cp=='\r')continue;
      if(cp=='\n'){pen=x;base_y+=(float)fc->line_height*scale;prev=0;continue;}
      if(cp=='\t'){GlyphRec *sp=get_glyph(fc,' ');pen+=(float)(sp?sp->advance:(I32)(fc->px/3u))*4.f*scale;prev=0;continue;}
      if(prev)pen+=(float)kerning(fc,prev,cp)*scale;
      GlyphRec *g=get_glyph(fc,cp);
      if(g){if(g->w&&g->h)draw_glyph(fc,dev,pen,base_y,scale,current_color,g);pen+=(float)g->advance*scale;drawn++;}
      prev=cp;
    }
    if(gD3DStageLogCount<=4u){log_msg("PHASE: glyph loop completed\r\n");log_dec("Glyphs processed",drawn);}
  }
restore:
  log_msg("PHASE: restoring D3D state\r\n");
  if(changed){
    if(have_fvf&&oldfvf){HRESULT r=setfvf(dev,oldfvf);if(r<0)log_hex("D3D restore FVF failed",(U32)r);}
    else if(have_decl){HRESULT r=setdecl(dev,olddecl);if(r<0)log_hex("D3D restore declaration failed",(U32)r);}
    if(have_vs){HRESULT r=setvs(dev,oldvs);if(r<0)log_hex("D3D restore vertex shader failed",(U32)r);}
    if(have_ps){HRESULT r=setps(dev,oldps);if(r<0)log_hex("D3D restore pixel shader failed",(U32)r);}
    /* DrawPrimitiveUP clears stream 0; put the game's original vertex buffer back. */
    if(have_stream&&oldstream){HRESULT r=setstream(dev,0,oldstream,oldstreamoffset,oldstreamstride);if(r<0)log_hex("D3D restore stream failed",(U32)r);}
    if(have_tex){HRESULT r=settex(dev,0,oldtex);if(r<0)log_hex("D3D restore texture failed",(U32)r);}
    for(U32 i=0;i<8;i++)setrs(dev,rsids[i],rsvals[i]);
    for(U32 i=0;i<6;i++)settss(dev,0,tssids[i],tssvals[i]);
    setsamp(dev,0,SAMP_MINFILTER,sampmin);setsamp(dev,0,SAMP_MAGFILTER,sampmag);
  }
  if(gD3DStageLogCount<=4u)log_msg("PHASE: D3D state restore complete\r\n");
cleanup:
  if(oldtex)((PFN_Release)(*(void***)oldtex)[2])(oldtex);
  if(olddecl)((PFN_Release)(*(void***)olddecl)[2])(olddecl);
  if(oldvs)((PFN_Release)(*(void***)oldvs)[2])(oldvs);
  if(oldps)((PFN_Release)(*(void***)oldps)[2])(oldps);
  if(oldstream)((PFN_Release)(*(void***)oldstream)[2])(oldstream);
  if(!states_captured&&gD3DStageLogCount<=4u)log_msg("PHASE: D3D render aborted before any state changes\r\n");
}

static void original_draw(void *self,const char *text,float x,float y,DWORD color,float scale) {
  typedef void (THISCALL *Fn)(void*,const char*,float,float,DWORD,float);
  ((Fn)0x004f4de0u)(self,text,x,y,color,scale);
}
static float original_measure(void *self,const char *text,float scale) {
  typedef float (THISCALL *Fn)(void*,const char*,float);
  return ((Fn)0x004f5720u)(self,text,scale);
}

/* GfxDevice vtable slot +0x204. Args: text, x-pixels, y-pixels, D3DCOLOR, scale. */
static U32 mode1_font_px(void *self,void *font) {
  U32 stride=0,fvf=0,px=16u,vertex_limit=0,scan_end=0,valid_glyphs=0;
  void *data=NULL;I32 lock_ok=0;float union_min=1.0e20f,union_max=-1.0e20f;
  if(!font)return px;
  for(U32 i=0;i<32u;i++)if(gMode1Fonts[i].valid&&gMode1Fonts[i].stream==font)return gMode1Fonts[i].px;
  fvf=*(volatile U32*)((U8*)font+8u);
  stride=*(volatile U32*)((U8*)font+0x0cu);
  vertex_limit=*(volatile U32*)((U8*)font+4u);
  if(gMode1InfoLogged<4u){
    log_msg("PHASE: mode=1 vertex-stream descriptor captured\r\n");
    log_hex("mode1 stream pointer",(U32)(uintptr_t)font);
    log_hex("mode1 stream FVF",fvf);
    log_dec("mode1 stream stride bytes",stride);
    log_dec("mode1 stream vertex limit",vertex_limit);
    log_hex("mode1 GfxDevice alignment",*(volatile U32*)((U8*)self+0x60cu));
  }
  if(stride>=8u&&stride<=128u&&vertex_limit>=4u&&vertex_limit<=65536u){
    U32 expected_stride=0u;
    /* D3DFVF 0x3D2 = XYZ(12)+NORMAL(12)+DIFFUSE(4)+SPECULAR(4)+TEX3(24). */
    if((fvf&0x4002u)==0x0002u)expected_stride+=12u;
    if(fvf&0x010u)expected_stride+=12u;
    if(fvf&0x040u)expected_stride+=4u;
    if(fvf&0x080u)expected_stride+=4u;
    U32 texcount=(fvf>>8)&0x0fu;
    for(U32 ti=0;ti<texcount&&ti<8u;ti++){
      U32 dimcode=(fvf>>(16u+ti*2u))&3u;
      U32 dim=(dimcode==0u)?2u:(dimcode==1u)?3u:(dimcode==2u)?4u:1u;
      expected_stride+=dim*4u;
    }
    log_dec("mode1 FVF decoded stride bytes",expected_stride);
    if(expected_stride&&expected_stride!=stride)log_msg("WARN: decoded FVF stride differs from stream stride\r\n");
    PFN_FontStreamLock lock=(PFN_FontStreamLock)0x004ec5c0u;
    PFN_FontStreamUnlock unlock=(PFN_FontStreamUnlock)0x004ec770u;
    lock_ok=lock(font,0u,0u,&data,0u);
    log_dec("mode1 font vertex stream lock result",(U32)lock_ok);
    log_hex("mode1 locked vertex data pointer",(U32)(uintptr_t)data);
    if(lock_ok&&data){
      scan_end=vertex_limit/4u;
      if(scan_end>127u)scan_end=127u;
      for(U32 cp=33u;cp<scan_end;cp++){
        U8 *base=(U8*)data+(cp*4u*stride);
        for(U32 v=0;v<4u;v++){
          float yy=*(volatile float*)(base+v*stride+4u);
          if(yy!=yy||yy>100000.f||yy< -100000.f)continue;
          if(yy<union_min)union_min=yy;
          if(yy>union_max)union_max=yy;
          valid_glyphs++;
        }
      }
      if(valid_glyphs&&union_max>=union_min){
        float h=union_max-union_min;
        if(h>=6.f&&h<=128.f){px=(U32)(h+0.5f);log_dec("mode1 scanned glyph count",scan_end>33u?scan_end-33u:0u);log_dec("mode1 valid vertex count",valid_glyphs);log_dec("mode1 source vertical span px",px);}
        else {log_hex("mode1 vertical span x100",(U32)(h*100.f));log_msg("WARN: mode1 vertical span out of expected range; using 16px TTF base\r\n");}
      } else log_msg("WARN: no readable glyph vertices; using 16px TTF base\r\n");
      unlock(font);
    } else log_msg("WARN: could not lock mode1 font vertex stream; using 16px TTF base\r\n");
  } else {
    log_msg("WARN: invalid mode1 stream stride/vertex limit; using 16px TTF base\r\n");
    log_dec("mode1 rejected stride",stride);log_dec("mode1 rejected vertex limit",vertex_limit);
  }
  if(px<6u||px>96u)px=16u;
  U32 slot=gMode1FontCursor++&31u;gMode1Fonts[slot].stream=font;gMode1Fonts[slot].px=px;gMode1Fonts[slot].valid=1u;
  if(gMode1InfoLogged<4u){log_dec("mode1 selected TTF pixel height",px);gMode1InfoLogged++;}
  return px;
}

void THISCALL TTF_DrawText(void *self,const char *text,float x,float y,DWORD color,float scale) {
  U32 mode,px;void *font=NULL;FontCache *fc;float original_scale=scale;
  if(!self||!text){original_draw(self,text,x,y,color,original_scale);return;}
  init_root();mode=*(volatile U32*)((U8*)self+0x5c4);
  if(mode==0u)font=*(void**)((U8*)self+0x5c8);else if(mode==1u)font=*(void**)((U8*)self+0x5cc);
  if(!gDrawLogged){log_msg("PHASE: DrawText hook entered\r\n");log_dec("DrawText mode",mode);log_hex("DrawText object",(U32)(uintptr_t)self);log_hex("DrawText font pointer",(U32)(uintptr_t)font);log_hex("DrawText D3DCOLOR ARGB",color);log_dec("DrawText arg2 x x100",(U32)(x*100.f));log_dec("DrawText arg3 y x100",(U32)(y*100.f));log_hex("DrawText scale x1000",(U32)(scale*1000.f));log_bytes("DrawText first bytes",text,32u);gDrawLogged=1u;}
  if((mode!=0u&&mode!=1u)||!font){log_msg("FALLBACK: DrawText unsupported mode or null font\r\n");original_draw(self,text,x,y,color,original_scale);return;}
  { const U8 *q=(const U8*)text;U32 visible=0,guard=0;while(*q&&guard++<MAX_TEXT_CHARS){if(q[0]==0x1bu){q+=(q[1]=='C'?10u:(q[1]?2u:1u));continue;}U32 c=*q;if(c==' '||c=='\t'||c=='\r'||c=='\n'){q++;continue;}visible=1u;break;}if(!visible){log_msg("PHASE: whitespace/control-only string skipped (no glyph draw required)\r\n");return;} }
  if(!init_ttf()){log_msg("FALLBACK: TTF initialization failed\r\n");original_draw(self,text,x,y,color,original_scale);return;}
  refresh_font_scale();scale*=((float)gFontScalePercent*0.01f);
  if(mode==0u)px=*(volatile U32*)((U8*)font+4u);else px=mode1_font_px(self,font);
  if(px<1u||px>128u){log_dec("FALLBACK: invalid font pixel size",px);original_draw(self,text,x,y,color,original_scale);return;}
  release_device_cache();fc=font_cache(px);if(!fc){log_msg("FALLBACK: font cache allocation failed\r\n");original_draw(self,text,x,y,color,original_scale);return;}
  if(gRenderLogCount++<8u)log_msg("PHASE: FontCache ready; atlas will be created on first visible glyph\r\n");
  if(mode==1u){
    U32 align=*(volatile U32*)((U8*)self+0x60cu);
    if(align==0u||align==1u){
      log_dec("PHASE: mode1 alignment selected",align);
      log_msg("PHASE: entering TTF width measurement for alignment\r\n");
      float width=measure(fc,text,scale);
      log_msg("PHASE: TTF width measurement returned\r\n");
      x-=align==0u?width:width*0.5f;
      if(gDrawLogged==1u){log_dec("mode1 text alignment",align);log_dec("mode1 aligned width x100",(U32)(width*100.f));log_dec("mode1 adjusted x x100",(U32)(x*100.f));}
    }
  }
  log_msg("PHASE: entering D3D render_text\r\n");
  render_text(fc,text,x,y,scale,color);
  log_msg("PHASE: D3D render_text returned\r\n");
}

float THISCALL TTF_MeasureText(void *self,const char *text,float scale) {
  U32 mode,px;void *font=NULL;FontCache *fc;float width;float original_scale=scale;
  if(!self||!text)return 0.f;
  init_root();mode=*(volatile U32*)((U8*)self+0x5c4);
  if(mode==0u)font=*(void**)((U8*)self+0x5c8);else if(mode==1u)font=*(void**)((U8*)self+0x5cc);
  if(!gMeasureLogged){log_msg("PHASE: MeasureText hook entered\r\n");log_dec("MeasureText mode",mode);log_hex("MeasureText font pointer",(U32)(uintptr_t)font);log_bytes("MeasureText first bytes",text,32u);gMeasureLogged=1u;}
  if((mode!=0u&&mode!=1u)||!font){log_msg("FALLBACK: MeasureText unsupported mode or null font\r\n");return original_measure(self,text,scale);}
  if(!init_ttf()){log_msg("FALLBACK: MeasureText TTF initialization failed\r\n");return original_measure(self,text,original_scale);}
  refresh_font_scale();scale*=((float)gFontScalePercent*0.01f);
  if(mode==0u)px=*(volatile U32*)((U8*)font+4u);else px=mode1_font_px(self,font);
  if(px<1u||px>128u){log_dec("FALLBACK: MeasureText invalid pixel size",px);return original_measure(self,text,original_scale);}
  release_device_cache();fc=font_cache(px);if(!fc){log_msg("FALLBACK: MeasureText font cache allocation failed\r\n");return original_measure(self,text,original_scale);}
  width=measure(fc,text,scale);
  if(gMeasureLogCount++<8u){log_dec("PHASE: TTF MeasureText width x100",(U32)(width*100.f));log_dec("MeasureText pixel size",px);}
  return width;
}

/* Keep two named symbols visible in the linker map for the PE injector. */
void TTF_EntryMarker(void) { (void)TTF_DrawText; (void)TTF_MeasureText; }
/* MSVC-compatible floating-point marker; no CRT is linked. */
int _fltused = 0;
