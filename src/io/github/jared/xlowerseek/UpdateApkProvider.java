package io.github.jared.xlowerseek;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;

/** Only an explicitly granted, read-only URI for the current verified APK is exposed. */
public final class UpdateApkProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 private File checked(Uri uri)throws FileNotFoundException{
  Context c=getContext();long id=UpdateDownload.id(c);
  if(id<0||!UpdateDownload.uri(c).equals(uri)||UpdateDownload.prefs(c).getLong("verified_id",-2)!=id||!UpdateDownload.ready(c).isFile())throw new FileNotFoundException("安装包尚未校验");return UpdateDownload.ready(c);
 }
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("只允许读取");return ParcelFileDescriptor.open(checked(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public String getType(Uri u){return "application/vnd.android.package-archive";}
 public Cursor query(Uri u,String[] p,String s,String[] a,String sort){try{File f=checked(u);String[] cols=p==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:p;MatrixCursor out=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="X-UP-update.apk";if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}out.addRow(row);return out;}catch(FileNotFoundException e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
