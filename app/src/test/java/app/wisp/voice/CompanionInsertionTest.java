package app.wisp.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionInsertionTest {
    private static final class Editor implements CompanionInsertion.Connection {
        String text;int start,end,commits;boolean nullAfter,drop;
        Editor(String text,int start,int end){this.text=text;this.start=start;this.end=end;}
        public CompanionInsertion.Surrounding read(int before){if(nullAfter&&commits>0)return null;int low=Math.min(start,end),high=Math.max(start,end),from=Math.max(0,low-before);return new CompanionInsertion.Surrounding(text.substring(from,high),start-from,end-from,from);}
        public void commit(String value){commits++;if(drop)return;int low=Math.min(start,end),high=Math.max(start,end);text=text.substring(0,low)+value+text.substring(high);start=end=low+value.length();}
    }
    @Test public void insertsAtCursorAndPreservesTextOnBothSides(){Editor e=new Editor("Привет мир",6,6);assertEquals(CompanionInsertion.Result.CONFIRMED,CompanionInsertion.insert(e,", новый"));assertEquals("Привет, новый мир",e.text);assertEquals(1,e.commits);}
    @Test public void replacesSelectionWithoutOverwritingRestOfEditor(){Editor e=new Editor("Старый API работает",6,0);assertEquals(CompanionInsertion.Result.CONFIRMED,CompanionInsertion.insert(e,"Новый "));assertEquals("Новый API работает",e.text);}
    @Test public void preservesMultilineEnglishTranslation(){Editor e=new Editor("",0,0);String text="Tasks:\n- Review the API.\n- Don't change the budget.";assertEquals(CompanionInsertion.Result.CONFIRMED,CompanionInsertion.insert(e,text));assertEquals(text,e.text);}
    @Test public void unavailableEditorIsRejectedWithoutSendingAnything(){int[] commits={0};var result=CompanionInsertion.insert(new CompanionInsertion.Connection(){public CompanionInsertion.Surrounding read(int n){return null;}public void commit(String s){commits[0]++;}},"private text");assertEquals(CompanionInsertion.Result.REJECTED,result);assertEquals(0,commits[0]);}
    @Test public void missingConfirmationNeverRetriesAndDuplicatesText(){Editor e=new Editor("Hello",5,5);e.nullAfter=true;assertEquals(CompanionInsertion.Result.UNCONFIRMED,CompanionInsertion.insert(e,"world"));assertEquals("Hello world",e.text);assertEquals(1,e.commits);}
    @Test public void identicalOldSuffixCannotFalselyConfirmADroppedCommit(){Editor e=new Editor("hello hello",11,11);e.drop=true;assertEquals(CompanionInsertion.Result.UNCONFIRMED,CompanionInsertion.insert(e,"hello"));assertEquals("hello hello",e.text);assertEquals(1,e.commits);}
}
