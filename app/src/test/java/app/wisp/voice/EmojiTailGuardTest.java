package app.wisp.voice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class EmojiTailGuardTest {
    @Test public void removesFiveEmojiFooterWithoutRewritingTheText(){
        assertEquals("Сегодня было хорошо. Завтра продолжим!",EmojiTailGuard.apply("сегодня было хорошо завтра продолжим","Сегодня было хорошо. Завтра продолжим! 😊🥰🔥🎉👍"));
        assertEquals("Don't change the API.",EmojiTailGuard.apply("не меняй API","Don't change the API.\n\n😊 ❤️ 🔥 🎉 👍"));
    }
    @Test public void aSingleAddedFinalEmojiIsRemovedTooIncludingBeforeClosingPunctuation(){
        assertEquals("Спасибо!",EmojiTailGuard.apply("спасибо","Спасибо! 🙏"));
        assertEquals("Спасибо!",EmojiTailGuard.apply("спасибо","Спасибо 🙏 !"));
        assertEquals("«Спасибо!»",EmojiTailGuard.apply("спасибо","«Спасибо! 🙏»"));
    }
    @Test public void prefixAndMiddleEmojiStayInTheirOriginalOutputPositions(){
        String text="🎉 Ура, отпуск! Завтра на море 🌊, а вечером вернёмся домой.";
        assertEquals(text,EmojiTailGuard.apply("ура отпуск завтра на море а вечером вернёмся домой",text));
        assertEquals("Завтра:\n- 🛒 Купить продукты.\n- 📦 Забрать посылку.",EmojiTailGuard.apply("купить продукты забрать посылку","Завтра:\n- 🛒 Купить продукты.\n- 📦 Забрать посылку. 😊🎉"));
    }
    @Test public void originalEmojiArePreservedAndOnlyExtraCopiesAreRemoved(){
        assertEquals("Спасибо! 🙏❤️",EmojiTailGuard.apply("спасибо 🙏❤️","Спасибо! 🙏❤️ 😊🎉"));
        assertEquals("Привет 👋, пока!",EmojiTailGuard.apply("привет 👋 пока","Привет 👋, пока! 👋👋"));
        assertEquals("Спасибо! ❤",EmojiTailGuard.apply("спасибо ❤️","Спасибо! ❤ 😊"));
        assertEquals("Спасибо! 🙏🙏🙏",EmojiTailGuard.apply("спасибо 🙏🙏🙏","Спасибо! 🙏🙏🙏"));
    }
    @Test public void handlesWholeFamiliesFlagsSkinTonesAndKeycapDigits(){
        assertEquals("Готово.",EmojiTailGuard.apply("готово","Готово. 👨‍👩‍👧‍👦 🇺🇸 👍🏽 🏳️‍🌈"));
        assertEquals("Готово. 👍🏽",EmojiTailGuard.apply("готово 👍🏽","Готово. 👍🏽 👨‍👩‍👧‍👦 🇺🇸"));
        assertEquals("Номера 12",EmojiTailGuard.apply("номера 1 2","Номера 1️⃣2️⃣"));
        assertEquals("Номер 1️⃣",EmojiTailGuard.apply("номер 1️⃣","Номер 1️⃣"));
    }
    @Test @Config(sdk=28) public void oldAndroidKeepsComplexEmojiWholeToo(){handlesWholeFamiliesFlagsSkinTonesAndKeycapDigits();}
    @Test public void neverDamagesCodeUrlsIdentifiersOrOrdinarySymbols(){
        for(String text:new String[]{"console.log(`✅`)","`✅`","```js\nconst emoji = '✅';\n```","https://example.com/🌊","mail@example.com","Марка™","© 2026","Цена 25 €","Версия 1.2.3"})assertEquals(text,EmojiTailGuard.apply("исходная речь",text));
        assertEquals("https://example.com/🌊",EmojiTailGuard.apply("ссылка","https://example.com/🌊 😊🎉"));
    }
    @Test public void preservesNumbersNegationPunctuationAndLists(){
        assertEquals("Не менять API v2. Бюджет: $1,234.50!",EmojiTailGuard.apply("не менять API v2 бюджет 1234.50","Не менять API v2. Бюджет: $1,234.50! 🤔 💰 😊"));
        assertEquals("1. Проверить API.\n2. Не удалять README.",EmojiTailGuard.apply("проверить API не удалять README","1. Проверить API.\n2. Не удалять README.\n\n💻😊🎉"));
    }
}
