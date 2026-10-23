package app.wisp.voice;

final class DictationText {
    private DictationText() {}
    static String insertion(String raw,CharSequence before,boolean replacesSelection) {
        String text=raw.trim();
        if(text.isEmpty()||replacesSelection||before==null||before.length()==0)return text;
        int previous=Character.codePointBefore(before,before.length());
        int first=text.codePointAt(0);
        boolean punctuation=".,!?;:)]}»".indexOf(first)>=0;
        boolean open="([{«\"".indexOf(previous)>=0;
        return !Character.isWhitespace(previous)&&!punctuation&&!open ? " "+text : text;
    }
    static boolean sensitive(int type) {
        int cls=type&android.text.InputType.TYPE_MASK_CLASS;
        int variation=type&android.text.InputType.TYPE_MASK_VARIATION;
        return (cls==android.text.InputType.TYPE_CLASS_NUMBER && variation==android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            || (cls==android.text.InputType.TYPE_CLASS_TEXT && (variation==android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            ||variation==android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    }
}
