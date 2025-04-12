## Setup
I had difficulty creating and running .jar file without it erroring for JavaFX, otherwise backend wise it does work, so for the Consumer, I suggest running it in Intellij.
Configuration:
--module-path "D:\stdiscm\discm_mediauploader\javafx-sdk-24\lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media,javafx.swing

Otherwise, the Producer can be run using "java -cp discm_mediauploader-1.0-SNAPSHOT.jar Producer".

The discm_mediauploader-1.0-SNAPSHOT.jar is in the /target folder.
