package app.wisp.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class DictationTextTest {
    @Test public void joinsDictationWithoutGluingWords(){assertEquals(" новый текст",DictationText.insertion("новый текст","ет",false));}
    @Test public void preservesExistingWhitespace(){assertEquals("hello",DictationText.insertion(" hello "," ",false));}
    @Test public void replacesSelectionWithoutExtraSpace(){assertEquals("замена",DictationText.insertion("замена","a",true));}
    @Test public void punctuationAndParenthesesDoNotGetAnExtraSpace(){assertEquals("!",DictationText.insertion("!","a",false));assertEquals("текст",DictationText.insertion("текст","(",false));}
    @Test public void emptyEditorAndEmptyTranscriptAreHandled(){assertEquals("Hello",DictationText.insertion("Hello",null,false));assertEquals("",DictationText.insertion("  ","a",false));}
}
