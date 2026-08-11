package com.gdonotice.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat

private enum class Page { HOME, ADD, SIZE, LIBRARY, DRAW, ACCOUNT }

/** A clean, reference-led prototype UI. Data and collaboration are intentionally added only after visual approval. */
class ReferenceSketchbookView(context: Context) : View(context) {
    private var page = Page.HOME
    private var selectedCanvasIndex = 1
    private val paper = Color.rgb(247, 240, 227)
    private val ink = Color.rgb(35, 35, 35)
    private val blue = Color.rgb(24, 73, 165)
    private val olive = Color.rgb(143, 153, 64)
    private val orange = Color.rgb(235, 121, 49)
    private val pink = Color.rgb(236, 150, 156)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cavorting = ResourcesCompat.getFont(context, R.font.cavorting) ?: Typeface.DEFAULT
    private val pretendard = ResourcesCompat.getFont(context, R.font.pretendard_regular) ?: Typeface.DEFAULT

    private fun s() = width / 390f
    private fun x(v: Number) = v.toFloat() * s()
    private fun y(v: Number) = v.toFloat() * s()
    private fun text(canvas: Canvas, value: String, px: Number, pxX: Number, pxY: Number, color: Int = ink, handwritten: Boolean = false, center: Boolean = false) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = x(px); paint.typeface = if (handwritten) cavorting else pretendard
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        canvas.drawText(value, x(pxX), y(pxY), paint)
    }
    private fun line(canvas: Canvas, a: Number, b: Number, c: Number, d: Number, color: Int = ink, width: Number = 1.5f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = x(width); paint.strokeCap = Paint.Cap.ROUND; paint.color = color; canvas.drawLine(x(a), y(b), x(c), y(d), paint) }
    private fun rect(canvas: Canvas, l: Number, t: Number, r: Number, b: Number, radius: Number = 0f, color: Int = Color.TRANSPARENT, stroke: Int? = null, width: Number = 1.5f) { paint.style = Paint.Style.FILL; paint.color = color; canvas.drawRoundRect(x(l), y(t), x(r), y(b), x(radius), x(radius), paint); if (stroke != null) { paint.style = Paint.Style.STROKE; paint.strokeWidth = x(width); paint.color = stroke; canvas.drawRoundRect(x(l), y(t), x(r), y(b), x(radius), x(radius), paint) } }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawColor(paper); when (page) { Page.HOME -> home(canvas); Page.ADD -> add(canvas); Page.SIZE -> size(canvas); Page.LIBRARY -> library(canvas); Page.DRAW -> drawPage(canvas); Page.ACCOUNT -> account(canvas) } }

    private fun header(canvas: Canvas, title: String, back: Boolean = true) { if (back) text(canvas, "‹", 42f, 20f, 58f, ink, true); text(canvas, title, 18f, if (back) 65f else 20f, 48f, ink, true) }
    private fun avatar(canvas: Canvas, cx: Float, cy: Float, r: Float = 22f) { paint.style = Paint.Style.FILL; paint.color = 0xFFDDE5AF.toInt(); canvas.drawCircle(x(cx), y(cy), x(r), paint); paint.style = Paint.Style.STROKE; paint.strokeWidth = x(1.3f); paint.color = ink; canvas.drawCircle(x(cx), y(cy), x(r), paint); canvas.drawCircle(x(cx - 8), y(cy - 5), x(3), paint); canvas.drawCircle(x(cx + 8), y(cy - 5), x(3), paint); path.reset(); path.moveTo(x(cx - 10), y(cy + 7)); path.quadTo(x(cx), y(cy + 15), x(cx + 10), y(cy + 7)); canvas.drawPath(path, paint) }
    private fun avatar(canvas: Canvas, cx: Int, cy: Int, r: Int) = avatar(canvas, cx.toFloat(), cy.toFloat(), r.toFloat())
    private fun nav(canvas: Canvas, activeLibrary: Boolean) { line(canvas, 0, 720, 390, 720, 0xFFBEB6AA.toInt(), 1f); val a = if (activeLibrary) 0xFF6C6C6C.toInt() else blue; val b = if (activeLibrary) blue else 0xFF6C6C6C.toInt(); homeIcon(canvas, 92f, 751f, a); notebookIcon(canvas, 296f, 751f, b); text(canvas, "Home", 11f, 92f, 783f, a, false, true); text(canvas, "Sketchbooks", 11f, 296f, 783f, b, false, true) }
    private fun homeIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) { path.reset(); path.moveTo(x(cx - 12), y(cy)); path.lineTo(x(cx), y(cy - 12)); path.lineTo(x(cx + 12), y(cy)); path.lineTo(x(cx + 10), y(cy)); path.lineTo(x(cx + 10), y(cy + 13)); path.lineTo(x(cx - 10), y(cy + 13)); path.close(); paint.style = Paint.Style.FILL; paint.color = color; canvas.drawPath(path, paint) }
    private fun notebookIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) { rect(canvas, cx - 9, cy - 13, cx + 10, cy + 13, 2f, Color.TRANSPARENT, color); for (v in -8..8 step 8) line(canvas, cx - 13, cy + v, cx - 9, cy + v, color) }
    private fun pencilIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) { canvas.save(); canvas.rotate(-38f, x(cx), y(cy)); rect(canvas, cx - 4, cy - 15, cx + 4, cy + 12, 3f, Color.TRANSPARENT, color); path.reset(); path.moveTo(x(cx - 4), y(cy + 12)); path.lineTo(x(cx), y(cy + 19)); path.lineTo(x(cx + 4), y(cy + 12)); canvas.drawPath(path, paint); canvas.restore() }
    private fun keyIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) { paint.style = Paint.Style.STROKE; paint.strokeWidth = x(2f); paint.color = color; canvas.drawCircle(x(cx - 7), y(cy - 6), x(7), paint); line(canvas, cx - 1, cy, cx + 14, cy + 15, color, 2f); line(canvas, cx + 7, cy + 9, cx + 12, cy + 4, color, 2f) }
    private fun plusIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) { line(canvas, cx - 14, cy, cx + 14, cy, color, 2f); line(canvas, cx, cy - 14, cx, cy + 14, color, 2f) }

    private fun notebookCard(canvas: Canvas, l: Float, t: Float, color: Int, title: String, animal: Int) { rect(canvas, l, t, l + 145, t + 182, 5f, color, ink, 1.2f); for (v in 12..168 step 14) { line(canvas, l - 3, t + v, l + 5, t + v, ink, 1.6f); paint.style = Paint.Style.FILL; paint.color = ink; canvas.drawCircle(x(l + 2), y(t + v), x(2.2f), paint) }; if (animal == 0) duck(canvas, l + 74, t + 81, 0.42f, Color.WHITE); else if (animal == 1) cat(canvas, l + 75, t + 85, 0.5f, Color.WHITE); else bunny(canvas, l + 75, t + 85, 0.48f, Color.WHITE); text(canvas, title, 15f, l + 73, t + 157, Color.WHITE, true, true) }
    private fun notebookCard(canvas: Canvas, l: Int, t: Int, color: Int, title: String, animal: Int) = notebookCard(canvas, l.toFloat(), t.toFloat(), color, title, animal)
    private fun duck(canvas: Canvas, cx: Float, cy: Float, q: Float, color: Int) { paint.style = Paint.Style.STROKE; paint.strokeWidth = x(3f * q); paint.color = color; canvas.drawOval(x(cx - 28*q), y(cy - 22*q), x(cx + 18*q), y(cy + 28*q), paint); canvas.drawCircle(x(cx + 12*q), y(cy - 29*q), x(16*q), paint); path.reset(); path.moveTo(x(cx + 25*q),y(cy - 27*q)); path.lineTo(x(cx + 47*q),y(cy - 21*q)); path.lineTo(x(cx + 25*q),y(cy - 15*q)); canvas.drawPath(path,paint); line(canvas,cx-11*q,cy+26*q,cx-18*q,cy+42*q,color,3*q); line(canvas,cx+4*q,cy+27*q,cx+14*q,cy+42*q,color,3*q) }
    private fun cat(canvas: Canvas, cx: Float, cy: Float, q: Float, color: Int) { paint.style=Paint.Style.STROKE; paint.strokeWidth=x(3*q); paint.color=color; path.reset(); path.moveTo(x(cx-25*q),y(cy-20*q)); path.lineTo(x(cx-12*q),y(cy-40*q)); path.lineTo(x(cx),y(cy-27*q)); path.lineTo(x(cx+15*q),y(cy-40*q)); path.lineTo(x(cx+26*q),y(cy-18*q)); canvas.drawOval(x(cx-26*q),y(cy-23*q),x(cx+26*q),y(cy+27*q),paint); canvas.drawPath(path,paint); canvas.drawCircle(x(cx-9*q),y(cy-4*q),x(2*q),paint); canvas.drawCircle(x(cx+9*q),y(cy-4*q),x(2*q),paint) }
    private fun bunny(canvas: Canvas, cx: Float, cy: Float, q: Float, color: Int) { paint.style=Paint.Style.STROKE; paint.strokeWidth=x(3*q); paint.color=color; canvas.drawOval(x(cx-22*q),y(cy-24*q),x(cx-5*q),y(cy+1*q),paint); canvas.drawOval(x(cx+5*q),y(cy-24*q),x(cx+22*q),y(cy+1*q),paint); canvas.drawCircle(x(cx),y(cy+15*q),x(23*q),paint) }

    private fun home(canvas: Canvas) { text(canvas,"Hi, Minjun",24f,20f,57f,ink,true); avatar(canvas,351f,48f,18f); rect(canvas,22,95,368,390,18f,0xFFFFFCF5.toInt(),ink,1.4f); for(v in 118..370 step 16){ line(canvas,18,v,27,v,ink,1.4f); paint.style=Paint.Style.FILL; paint.color=ink; canvas.drawCircle(x(21),y(v),x(2.5f),paint) }; duck(canvas,197f,248f,1.3f,ink); avatar(canvas,53,368,23); text(canvas,"2h",12f,335f,372f,ink); action(canvas,65,484,ReferenceIcon.PENCIL,olive,"Continue"); action(canvas,195,484,ReferenceIcon.PLUS,blue,"New"); action(canvas,325,484,ReferenceIcon.KEY,orange,"Invite"); nav(canvas,false) }
    private enum class ReferenceIcon { PENCIL, NOTEBOOK, KEY, PLUS }
    private fun action(canvas: Canvas,cx:Float,cy:Float,icon:ReferenceIcon,color:Int,label:String){ paint.style=Paint.Style.STROKE; paint.strokeWidth=x(1.3f); paint.color=color; canvas.drawCircle(x(cx),y(cy),x(29),paint); when(icon){ReferenceIcon.PENCIL->pencilIcon(canvas,cx,cy,color);ReferenceIcon.KEY->keyIcon(canvas,cx,cy,color);ReferenceIcon.PLUS->plusIcon(canvas,cx,cy,color);else->notebookIcon(canvas,cx,cy,color)}; text(canvas,label,12f,cx,cy+50,ink,false,true) }
    private fun action(canvas: Canvas,cx:Int,cy:Int,icon:ReferenceIcon,color:Int,label:String) = action(canvas,cx.toFloat(),cy.toFloat(),icon,color,label)
    private fun add(canvas: Canvas) { home(canvas); paint.style=Paint.Style.FILL; paint.color=0x99000000.toInt(); canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); rect(canvas,0,390,390,820,28f,paper); text(canvas,"×",35f,350f,432f,ink,true); action(canvas,140,522,ReferenceIcon.NOTEBOOK,blue,"New"); action(canvas,260,522,ReferenceIcon.KEY,orange,"Join") }
    private fun size(canvas: Canvas) { header(canvas,"New sketchbook"); val labels=listOf("A4","A5","B5","1:1","4:3","9:16"); labels.forEachIndexed { i,label -> val col=i%3; val row=i/3; val l=38+col*110; val t=105+row*172; val h=if(i<3) 99f else if(i==5) 115f else 72f; val selected=i==selectedCanvasIndex; rect(canvas,l,t,l+63,t+h,2f,Color.TRANSPARENT, if(selected) blue else 0xFF77726B.toInt(), if(selected)2f else 1.3f); if(i<3) { line(canvas,l+45,t,l+45,t+18,ink); line(canvas,l+45,t+18,l+63,t+18,ink) }; text(canvas,label,14f,l+31,t+h+26,ink,false,true); if(selected){ paint.style=Paint.Style.FILL; paint.color=blue; canvas.drawCircle(x(l+51),y(t+11),x(9),paint); text(canvas,"✓",11f,l+51,t+15,Color.WHITE,false,true) } }; text(canvas,"⚙  Custom size",13f,22f,674f,blue); rect(canvas,271,647,368,698,28f,blue); text(canvas,"Create",16f,319f,679f,Color.WHITE,false,true) }
    private fun library(canvas: Canvas) { header(canvas,"Sketchbooks",false); avatar(canvas,351,48,18); notebookCard(canvas,27,102,blue,"As Usual",0); notebookCard(canvas,214,102,olive,"Cat Days",1); notebookCard(canvas,27,316,orange,"Trip",2); notebookCard(canvas,214,316,0xFFF0C73D.toInt(),"Recipes",1); notebookCard(canvas,27,530,pink,"Pond",1); notebookCard(canvas,214,530,0xFFD7CDAF.toInt(),"Ideas",2); paint.style=Paint.Style.FILL; paint.color=blue; canvas.drawCircle(x(347),y(695),x(25),paint); plusIcon(canvas,347f,695f,Color.WHITE); nav(canvas,true) }
    private fun drawPage(canvas: Canvas) { header(canvas,"As Usual"); text(canvas,"✓",22f,275f,51f,olive,true); avatar(canvas,322,48,16); avatar(canvas,354,48,16); duck(canvas,202f,344f,2.6f,ink); rect(canvas,21,654,369,716,28f,0xFFFFFCF5.toInt(),0xFFD4CCBE.toInt(),1f); pencilIcon(canvas,64f,684f,ink); paint.style=Paint.Style.FILL; paint.color=blue; canvas.drawCircle(x(116),y(684),x(10),paint); keyIcon(canvas,163f,684f,ink); text(canvas,"↶",32f,211f,696f,ink,true); text(canvas,"…",29f,324f,693f,ink,true) }
    private fun account(canvas: Canvas) { text(canvas,"×",35f,22f,56f,ink,true); avatar(canvas,195,159,54); text(canvas,"Minjun",28f,195f,246f,ink,true,true); text(canvas,"G  minjun.kim@gmail.com",14f,195f,274f,0xFF6B655E.toInt(),false,true); val rows=listOf("Edit avatar","Notifications","Sync & storage","Account"); rows.forEachIndexed{ i,v->rect(canvas,21,318+i*76,369,373+i*76,15f,0xFFFFFCF5.toInt(),0xFFE0D8C9.toInt(),1f); text(canvas,v,16f,78f,352+i*76,ink); text(canvas,"›",28f,340f,353+i*76,ink,true)}; text(canvas,"Version 3.0.0  ·  Terms  ·  Privacy",11f,195f,740f,0xFF77726B.toInt(),false,true) }
    override fun onTouchEvent(event: MotionEvent): Boolean { if(event.action != MotionEvent.ACTION_UP) return true; val px=event.x/s(); val py=event.y/s(); page=when(page){Page.HOME-> when { py>440&&py<550&&px in 150f..240f->Page.ADD; px>325&&py<90->Page.ACCOUNT; py>720&&px>190->Page.LIBRARY; else->Page.HOME }; Page.ADD->if(py>470&&py<580&&px<210) Page.SIZE else Page.ADD; Page.SIZE-> { CanvasSizeInteraction.presetIndex(px, py)?.let { selectedCanvasIndex = it }; if(CanvasSizeInteraction.shouldCreate(py)) Page.LIBRARY else Page.SIZE }; Page.LIBRARY->if(py in 95f..300f&&px<200) Page.DRAW else Page.LIBRARY; Page.DRAW->if(py<85&&px<65) Page.LIBRARY else Page.DRAW; Page.ACCOUNT->if(py<85&&px<65)Page.HOME else Page.ACCOUNT}; invalidate(); return true }
}
