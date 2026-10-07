package com.minenorth_eurobank.braquage.client;

import com.minenorth_eurobank.braquage.network.ModNetwork;
import com.minenorth_eurobank.braquage.item.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class HackingScreen extends Screen {
    private final List<Integer> sequence; private int progress=0; private final long endTime,sequenceStart; private int lastVisible=-2;
    private static final long STEP_MS=650L,INTRO_MS=900L,TOTAL_MS=30000L;
    public HackingScreen(List<Integer> sequence){super(Component.literal("Piratage MineNorth"));this.sequence=List.copyOf(sequence);long now=System.currentTimeMillis();sequenceStart=now;endTime=now+TOTAL_MS;}
    public void setProgress(int index,int total){progress=index;}
    private boolean showing(){return System.currentTimeMillis()-sequenceStart<INTRO_MS+sequence.size()*STEP_MS;}
    private int visible(){long e=System.currentTimeMillis()-sequenceStart;if(e<INTRO_MS)return -1;int s=(int)((e-INTRO_MS)/STEP_MS);return s>=0&&s<sequence.size()?s:-1;}
    @Override public void tick(){long now=System.currentTimeMillis();if(now>=endTime){onClose();return;}int v=visible();if(v!=lastVisible){if(v>=0)play(ModSounds.HACK_BEEP.get(),.72f,1f+v*.03f);lastVisible=v;}}
    private void play(net.minecraft.sounds.SoundEvent sound,float vol,float pitch){if(minecraft!=null&&minecraft.level!=null&&minecraft.player!=null)minecraft.level.playLocalSound(minecraft.player.getX(),minecraft.player.getY(),minecraft.player.getZ(),sound,net.minecraft.sounds.SoundSource.MASTER,vol,pitch,false);}
    private int cell(){return Math.max(48,Math.min(72,Math.min((width-190)/3,(height-260)/2)));} private int gap(){return 8;} private int gridW(){return cell()*3+gap()*2;} private int left(){return(width-gridW())/2;} private int top(){return Math.max(125,(height-(cell()*2+gap()))/2+42);}
    /** Texte net : aucun doublage/ombre, pour rester lisible sur l'interface claire. */
    private void centered(GuiGraphics g, String text, int y, int color) {
        g.drawString(font, text, width / 2 - font.width(text) / 2, y, color, false);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        renderBackground(g);
        int c=cell(),gap=gap(),l=left(),t=top(),gh=c*2+gap(),remaining=Math.max(0,(int)Math.ceil((endTime-System.currentTimeMillis())/1000.0)),v=visible();
        int px=Math.max(12,l-40),pr=Math.min(width-12,l+gridW()+40),pb=Math.min(height-10,t+gh+48);
        // Interface claire et lisible : aucun texte avec ombre.
        g.fill(px+5,10,pr+5,pb+5,0x33000000);
        g.fill(px,10,pr,pb,0xFFF7F9FB);
        g.renderOutline(px,10,pr-px,pb-10,0xFF8EA6BA);
        g.fill(px,10,pr,68,MineNorthStyle.NAVY);
        g.drawString(font,MineNorthStyle.bold("MINE NORTH - PIRATAGE"),px+18,22,0xFFFFFFFF,false);
        centered(g,showing()?"MÉMORISEZ LA SÉQUENCE":"REPRODUISEZ LA SÉQUENCE",80,showing()?MineNorthStyle.CYAN:MineNorthStyle.GREEN);
        centered(g,"Progression : "+progress+" / "+sequence.size(),101,MineNorthStyle.TEXT);
        centered(g,"Temps restant : "+remaining+" s",122,remaining<=10?MineNorthStyle.ALERT:MineNorthStyle.WARN);
        // Petit bandeau d'instruction pour éviter que le texte se perde sur le fond.
        int infoY=136;
        g.fill(l-16,infoY,l+gridW()+16,infoY+24,0xFFE8EEF3);
        g.renderOutline(l-16,infoY,gridW()+32,24,0xFFC2CED8);
        centered(g,showing()?"OBSERVEZ LES CASES QUI S'ALLUMENT":"CLIQUEZ DANS LE BON ORDRE",infoY+7,0xFF173B68);
        for(int i=0;i<6;i++){
            int col=i%3,row=i/3,x=l+col*(c+gap),y=t+row*(c+gap),seq=sequence.indexOf(i);
            boolean current=showing()&&v>=0&&sequence.get(v)==i,done=seq>=0&&seq<progress,hov=!showing()&&mx>=x&&mx<x+c&&my>=y&&my<y+c;
            int color=current?MineNorthStyle.CYAN:done?MineNorthStyle.GREEN:hov?MineNorthStyle.HOVER:0xFFDFE7ED;
            g.fill(x+3,y+4,x+c+3,y+c+4,0x26000000);
            g.fill(x,y,x+c,y+c,color);
            g.renderOutline(x,y,c,c,current?0xFF0B7DAF:0xFF9FB0BE);
            String label=current?String.valueOf(i+1):done?"✓":String.valueOf(i+1);
            int textColor=current||done?0xFFFFFFFF:0xFF173B68;
            g.drawString(font,label,x+c/2-font.width(label)/2,y+c/2-4,textColor,false);
        }
        super.render(g,mx,my,pt);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0||showing())return true;int c=cell(),gap=gap(),l=left(),t=top();for(int i=0;i<6;i++){int col=i%3,row=i/3,x=l+col*(c+gap),y=t+row*(c+gap);if(mx>=x&&mx<x+c&&my>=y&&my<y+c){play(ModSounds.HACK_BEEP.get(),.55f,1.12f);ModNetwork.CHANNEL.sendToServer(new ModNetwork.HackClickPacket(i));return true;}}return true;}
    @Override public void onClose(){ModNetwork.CHANNEL.sendToServer(new ModNetwork.CloseHackPacket());super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
