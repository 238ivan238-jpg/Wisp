package app.wisp.voice;

import java.util.Arrays;

/** Three envelopes of recent real microphone peaks; no synthetic oscillation. */
final class VoiceLevel {
    private final float[] levels=new float[3],targets=new float[3];
    private float previous,older;
    private long frame=-1;
    static float normalize(int amplitude){
        if(amplitude<=0)return 0;
        // dBFS: a small noise gate, with a logarithmic range suitable for speech.
        double db=20*Math.log10(Math.min(32767,amplitude)/32767d);
        return (float)Math.max(0,Math.min(1,(db+48)/40));
    }
    void sample(int amplitude,long now){
        advance(now);float current=normalize(amplitude);
        targets[0]=previous*.82f;targets[1]=current;targets[2]=older*.68f;
        older=previous;previous=current;
    }
    void advance(long now){
        if(frame<0){frame=now;return;}
        float elapsed=Math.min(250,Math.max(0,now-frame));frame=now;
        for(int i=0;i<3;i++){
            float tau=targets[i]>levels[i]?45:180;
            levels[i]+=(targets[i]-levels[i])*(float)(1-Math.exp(-elapsed/tau));
            if(Math.abs(targets[i]-levels[i])<.003f)levels[i]=targets[i];
        }
    }
    void snap(){System.arraycopy(targets,0,levels,0,3);}
    float level(int index){return levels[index];}
    boolean moving(){for(int i=0;i<3;i++)if(levels[i]!=targets[i])return true;return false;}
    void reset(){Arrays.fill(levels,0);Arrays.fill(targets,0);previous=older=0;frame=-1;}
}
