package app.wisp.voice;

final class ApiKey {
    private ApiKey() {}
    /** Remove invisible copy/paste separators without logging or displaying credentials. */
    static String normalize(String input) {
        if(input==null)return "";
        StringBuilder out=new StringBuilder();
        input.codePoints().filter(c->!Character.isWhitespace(c)&&!Character.isSpaceChar(c)
            && c!=0x200b&&c!=0xfeff).forEach(out::appendCodePoint);
        return out.toString();
    }
}
