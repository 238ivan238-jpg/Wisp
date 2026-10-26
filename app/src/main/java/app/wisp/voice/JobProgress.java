package app.wisp.voice;

record JobProgress(Stage stage,int part,int total) {
    enum Stage {PREPARING,RECOGNIZING,EDITING,TRANSLATING,INSERTING}
    JobProgress(Stage stage){this(stage,0,0);}
    String label(){return switch(stage){
        case PREPARING->"Подготавливаю запись…";
        case RECOGNIZING->total>1?"Распознаю часть "+part+" из "+total+"…":"Распознаю речь…";
        case EDITING->"Исправляю текст…";
        case TRANSLATING->"Перевожу на английский…";
        case INSERTING->"Вставляю текст…";
    };}
}
