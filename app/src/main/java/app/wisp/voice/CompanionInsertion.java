package app.wisp.voice;

/** A cursor-based insertion; never replaces the whole editor or uses the clipboard. */
final class CompanionInsertion {
    enum Result {CONFIRMED,UNCONFIRMED,SENT_WITHOUT_CONTEXT,REJECTED}
    record Surrounding(String text,int start,int end,int offset) {}
    interface Connection {Surrounding read(int before);void commit(String text);}
    private static boolean valid(Surrounding s){return s!=null&&s.text()!=null&&s.start()>=0&&s.end()>=0&&s.start()<=s.text().length()&&s.end()<=s.text().length();}
    private static Surrounding read(Connection connection,int before){
        try{return connection.read(before);}catch(UnsupportedOperationException unsupported){return null;}
    }
    static Result insert(Connection connection,String text){
        if(connection==null||text==null||text.trim().isEmpty())return Result.REJECTED;
        Surrounding before=read(connection,2);
        if(before==null){
            // Some editors implement commitText but not getSurroundingText. The
            // live input connection can still insert at its own cursor/selection.
            // With no context, do not guess spaces or claim read-back confirmation.
            connection.commit(text.trim());return Result.SENT_WITHOUT_CONTEXT;
        }
        if(!valid(before))return Result.REJECTED;
        int start=Math.min(before.start(),before.end());
        String insertion=DictationText.insertion(text,before.text().substring(0,start),before.start()!=before.end());
        connection.commit(insertion);
        // Accessibility's commitText is void. Check read-back, never retry an
        // unconfirmed commit: a retry could duplicate text already inserted.
        Surrounding after=read(connection,Math.min(16384,insertion.length()+2));
        if(!valid(after)||after.start()!=after.end()||insertion.length()>16384||before.offset()<0||after.offset()<0)return Result.UNCONFIRMED;
        if(after.offset()+after.start()!=before.offset()+start+insertion.length())return Result.UNCONFIRMED;
        return after.text().substring(0,after.start()).endsWith(insertion)?Result.CONFIRMED:Result.UNCONFIRMED;
    }
}
