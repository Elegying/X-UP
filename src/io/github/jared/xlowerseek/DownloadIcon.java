package io.github.jared.xlowerseek;
import android.graphics.*;
import android.graphics.drawable.Drawable;
final class DownloadIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean busy;
    private int progress=-1;
    private final Runnable frame=()->{invalidateSelf();};
    void setBusy(boolean value,int percent){
        busy=value;progress=percent;
        if(!busy||progress>=0)unscheduleSelf(frame);
        invalidateSelf();
    }
    @Override public boolean setVisible(boolean visible,boolean restart){
        if(!visible)unscheduleSelf(frame);
        boolean changed=super.setVisible(visible,restart);
        if(visible&&busy)invalidateSelf();
        return changed;
    }
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path p=new Path();p.moveTo(12,3);p.lineTo(12,15);p.moveTo(7,10);p.lineTo(12,15);p.lineTo(17,10);p.moveTo(4,16);p.lineTo(4,21);p.lineTo(20,21);p.lineTo(20,16);canvas.drawPath(p,paint);
        if(busy){paint.setColor(0xFF66C7FF);paint.setStrokeWidth(1.5f);float start=progress<0?(android.os.SystemClock.uptimeMillis()%1200)*0.3f-90:-90;
            canvas.drawArc(1,1,23,23,start,progress<0?90:Math.max(12,progress*3.6f),false,paint);
            if(progress<0&&isVisible()){unscheduleSelf(frame);scheduleSelf(frame,android.os.SystemClock.uptimeMillis()+32);}}
        canvas.restore();
    }
    @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
}
