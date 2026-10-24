package app.wisp.voice;

import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.icu.text.BreakIterator;
import java.util.*;
import java.util.regex.Pattern;

/** Removes only newly introduced terminal decoration; never moves emoji or rewrites words. */
final class EmojiTailGuard {
    private record Span(int start,int end,boolean emoji) {}
    private static final Pattern PROTECTED=Pattern.compile("(?s)```.*?(?:```|$)|~~~.*?(?:~~~|$)|`[^`]*(?:`|$)|(?:https?://|www\\.)\\S+|\\S+@\\S+");
    private static final String CLOSING=".,!?…;:\"'»”’)]}*_~";
    static String apply(String original,String edited){
        List<Span> spans=spans(edited);
        Map<String,Integer> budget=new HashMap<>();
        for(Span span:spans(original))if(span.emoji)budget.merge(key(original.substring(span.start,span.end)),1,Integer::sum);
        boolean[] added=new boolean[spans.size()],protectedText=new boolean[edited.length()];
        var matches=PROTECTED.matcher(edited);while(matches.find())Arrays.fill(protectedText,matches.start(),matches.end(),true);
        // Existing occurrences are consumed from left to right. A repeated copy in
        // the footer cannot borrow protection from an emoji already kept in the body.
        for(int i=0;i<spans.size();i++){
            Span span=spans.get(i);if(!span.emoji)continue;
            String k=key(edited.substring(span.start,span.end));int remaining=budget.getOrDefault(k,0);
            added[i]=remaining==0;if(remaining>0)budget.put(k,remaining-1);
        }
        boolean[] remove=new boolean[edited.length()];boolean changed=false;
        for(int i=spans.size()-1;i>=0;i--){
            Span span=spans.get(i);
            if(span.emoji){
                if(added[i]&&!protectedText[span.start]){
                    int first=edited.codePointAt(span.start);
                    if(first<128&&(Character.isDigit(first)||first=='#'||first=='*')){
                        // A keycap contains a real digit/list marker. Remove its
                        // emoji presentation only; never discard the number itself.
                        Arrays.fill(remove,span.start+1,span.end,true);changed=true;continue;
                    }
                    int from=span.start,to=span.end;
                    while(from>0&&space(edited.codePointBefore(from)))from-=Character.charCount(edited.codePointBefore(from));
                    while(to<edited.length()&&space(edited.codePointAt(to)))to+=Character.charCount(edited.codePointAt(to));
                    Arrays.fill(remove,from,to,true);changed=true;
                }
            }else if(!separator(edited,span))break;
        }
        if(!changed)return edited;
        StringBuilder output=new StringBuilder();for(int i=0;i<edited.length();i++)if(!remove[i])output.append(edited.charAt(i));
        return output.toString().stripTrailing();
    }
    private static String key(String emoji){return emoji.replace("\uFE0E","").replace("\uFE0F","");}
    private static boolean space(int cp){return Character.isWhitespace(cp)||Character.isSpaceChar(cp);}
    private static boolean separator(String text,Span span){
        return text.substring(span.start,span.end).codePoints().allMatch(cp->space(cp)||CLOSING.indexOf(cp)>=0);
    }
    private static List<Span> spans(String text){
        List<Span> result=new ArrayList<>();BreakIterator it=BreakIterator.getCharacterInstance(Locale.ROOT);it.setText(text);
        for(int start=it.first(),end=it.next();end!=BreakIterator.DONE;start=end,end=it.next()){
            boolean emoji=false,variation=false;
            for(int at=start;at<end;){int cp=text.codePointAt(at);at+=Character.charCount(cp);
                if(cp==0x20E3){emoji=true;break;}
                if(cp==0xFE0F)variation=true;
                if(cp>=0x2300&&UCharacter.hasBinaryProperty(cp,UProperty.EMOJI))emoji=true;
            }
            // ASCII digits/#/* and ordinary ©/®/™ remain text unless explicitly
            // presented as an emoji. Grapheme iteration keeps ZWJ, flags and tones whole.
            if(variation&&UCharacter.hasBinaryProperty(text.codePointAt(start),UProperty.EMOJI))emoji=true;
            result.add(new Span(start,end,emoji));
        }
        return result;
    }
    private EmojiTailGuard(){}
}
