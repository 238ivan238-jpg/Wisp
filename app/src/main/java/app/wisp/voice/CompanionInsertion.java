package app.wisp.voice;

/** A cursor-based insertion; never replaces the whole editor or uses the clipboard. */
final class CompanionInsertion {
    enum Result {CONFIRMED,UNCONFIRMED,REJECTED}
    record Surrounding(String text,int start,int end,int offset) {}
    interface Connection {Surrounding read(int before);void commit(String text);}
    private static boolean valid(Surrounding s){return s!=null&&s.text()!=null&&s.start()>=0&&s.end()>=0&&s.start()<=s.text().length()&&s.end()<=s.text().length();}
    static Result insert(Connection connection,String text){
        Surrounding before=connection.read(2);if(!valid(before)||text==null||text.trim().isEmpty())return Result.REJECTED;
        int start=Math.min(before.start(),before.end());
        String insertion=DictationText.insertion(text,before.text().substring(0,start),before.start()!=before.end());
        connection.commit(insertion);
        // Accessibility's commitText is void. Check read-back, never retry an
        // unconfirmed commit: a retry could duplicate text already inserted.
        Surrounding after=connection.read(Math.min(16384,insertion.length()+2));
        if(!valid(after)||after.start()!=after.end()||insertion.length()>16384||before.offset()<0||after.offset()<0)return Result.UNCONFIRMED;
        if(after.offset()+after.start()!=before.offset()+start+insertion.length())return Result.UNCONFIRMED;
        return after.text().substring(0,after.start()).endsWith(insertion)?Result.CONFIRMED:Result.UNCONFIRMED;
    }
}
