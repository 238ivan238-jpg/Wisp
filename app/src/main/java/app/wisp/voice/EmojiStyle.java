package app.wisp.voice;

/** Style strength changes local placement, never permits a decorative footer. */
enum EmojiStyle {
    RESTRAINED("restrained","Сдержанно","\nEmoji intensity: restrained. Usually use none. Add a single locally meaningful emoji only for a clear emotional moment; never decorate neutral clauses.\n"),
    MODERATE("moderate","Умеренно",""),
    ACTIVE("active","Активно","\nEmoji intensity: active. Use emoji at several distinct suitable ideas or list items when the text supports it, instead of reserving them for the closing line. One per local idea, never adjacent, no count quota and no final emoji tail. Serious/formal subjects still take priority over decoration.\n");
    final String code,label,instruction;
    EmojiStyle(String code,String label,String instruction){this.code=code;this.label=label;this.instruction=instruction;}
    static EmojiStyle parse(String value){for(var style:values())if(style.code.equals(value))return style;return MODERATE;}
}
