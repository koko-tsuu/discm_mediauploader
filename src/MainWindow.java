import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Screen;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class MainWindow {

    private JFrame window;
    private JPanel cardLayoutPanel;
    private JPanel gridLayoutPanel;
    private JPanel videoPlayerPanel;
    private JPanel wrapperPanel;
    private int rowCount = 0;

    private String[] cardLayoutStrings = {"Video Grid Layout", "Video Player"};


        public MainWindow(){

            window = new JFrame();
            window.setTitle("Video Viewer");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);
            window.setSize(800, 600);
            window.setLocationRelativeTo(null);




            cardLayoutPanel = new JPanel(new CardLayout());

            gridLayoutPanel = new JPanel(new GridBagLayout());
            videoPlayerPanel = new JPanel(new BorderLayout());

            wrapperPanel = new JPanel(new BorderLayout());
            wrapperPanel.add(gridLayoutPanel, BorderLayout.NORTH);
            JScrollPane downloadedVideosScrollPane = new JScrollPane(wrapperPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            cardLayoutPanel.add(downloadedVideosScrollPane, cardLayoutStrings[0]);
            cardLayoutPanel.add(videoPlayerPanel, cardLayoutStrings[1]);

            CardLayout cardLayout = (CardLayout) cardLayoutPanel.getLayout();
            cardLayout.show(cardLayoutPanel, cardLayoutStrings[0]);

            gridLayoutPanel.setPreferredSize(new Dimension(800, 600));

            window.add(cardLayoutPanel);



        }

        synchronized void changeMedia(String filename, int retries){
            videoPlayerPanel.removeAll();



            File video = new File(System.getProperty("user.dir") + "\\output\\compressed\\" + filename);
            Media m = new Media(video.toURI().toString());
            MediaPlayer player = new MediaPlayer(m);
            MediaView viewer = new MediaView(player);

            if (player.getError() != null) {
                if (retries > 0) {
                    changeMedia(filename, retries - 1);
                }
                else {
                    System.out.println("Failed to load the video " + filename + ": " + player.getError());
                }
            }
            else {
                loadVideo(viewer, player);
            }


        }

        void loadVideo(MediaView viewer, MediaPlayer player)
        {
            Platform.runLater(()-> {
                // video instantiate
                final JFXPanel vfxPanel = new JFXPanel();

                StackPane root = new StackPane();
                Scene scene = new Scene(root);

                // center video position
                javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
                viewer.setX((screen.getWidth() - videoPlayerPanel.getWidth()) / 2);
                viewer.setY((screen.getHeight() - videoPlayerPanel.getHeight()) / 2);

                // resize video based on screen size
                DoubleProperty width = viewer.fitWidthProperty();
                DoubleProperty height = viewer.fitHeightProperty();
                width.bind(Bindings.selectDouble(viewer.sceneProperty(), "width"));
                height.bind(Bindings.selectDouble(viewer.sceneProperty(), "height"));
                viewer.setPreserveRatio(true);

                // add video to stackpane
                root.getChildren().add(viewer);

                vfxPanel.setScene(scene);

                videoPlayerPanel.add(vfxPanel, BorderLayout.CENTER);



                JPanel playerButtons = new JPanel(new FlowLayout());

                JButton pauseButton = new JButton("Pause");
                pauseButton.addActionListener(e -> {
                    player.pause();
                });
                JButton playButton = new JButton("Play");
                playButton.addActionListener(e -> {
                    player.play();
                });
                JButton stopButton = new JButton("Stop");
                stopButton.addActionListener(e -> {
                    player.stop();
                });

                playerButtons.add(pauseButton);
                playerButtons.add(playButton);
                playerButtons.add(stopButton);

                videoPlayerPanel.add(playerButtons, BorderLayout.PAGE_END);

                JButton backButton = new JButton("Back");
                backButton.addActionListener((ActionEvent ae) ->
                {
                    CardLayout cardLayout = (CardLayout) cardLayoutPanel.getLayout();
                    cardLayout.show(cardLayoutPanel, cardLayoutStrings[0]);
                    player.stop();
                });
                videoPlayerPanel.add(backButton, BorderLayout.PAGE_START);



                videoPlayerPanel.revalidate();
                videoPlayerPanel.repaint();

                cardLayoutPanel.revalidate();
                cardLayoutPanel.repaint();
            });

        }


        synchronized void addDownloadedVideoUI(String filename, int retries)
        {

            JLabel videoNameLabel = new JLabel(filename);
            final JFXPanel vfxPanel = new JFXPanel();

            File video = new File(System.getProperty("user.dir") + "\\output\\compressed\\" + filename);

            Media m = new Media(video.toURI().toString());
            MediaPlayer player = new MediaPlayer(m);
            MediaView viewer = new MediaView(player);

            if (player.getError() != null) {
                if (retries > 0) {
                    addDownloadedVideoUI(filename, retries - 1);
                }
                else {
                    System.out.println("Failed to load the video " + filename + ": " + player.getError());
                }
            }
            else {

                Platform.runLater(() -> {


                    StackPane root = new StackPane();
                    Scene scene = new Scene(root);

                    // center video position
                    javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
                    viewer.setX((screen.getWidth() - gridLayoutPanel.getWidth()) / 2);
                    viewer.setY((screen.getHeight() - gridLayoutPanel.getHeight()) / 2);


                    // resize video based on screen size
                    DoubleProperty width = viewer.fitWidthProperty();
                    DoubleProperty height = viewer.fitHeightProperty();
                    width.bind(Bindings.selectDouble(viewer.sceneProperty(), "width"));
                    height.bind(Bindings.selectDouble(viewer.sceneProperty(), "height"));
                    viewer.setPreserveRatio(true);


                    // add video to stackpane
                    root.getChildren().add(viewer);

                    vfxPanel.setScene(scene);


                    vfxPanel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            super.mouseEntered(e);
                            player.play();
                            player.setStopTime(Duration.millis(10000.0));
                            player.setOnEndOfMedia(new Runnable() {
                                @Override
                                public void run() {
                                    player.stop();

                                }
                            });
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            super.mouseExited(e);
                            player.stop();
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            super.mouseClicked(e);
                            changeMedia(filename, 3);
                            CardLayout cardLayout = (CardLayout) cardLayoutPanel.getLayout();
                            cardLayout.show(cardLayoutPanel, cardLayoutStrings[1]);
                        }
                    });


                    GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
                    gridBagConstraints2.insets = new Insets(5, 5, 5, 5);
                    gridBagConstraints2.gridy = rowCount;
                    gridBagConstraints2.gridx = 0;
                    gridBagConstraints2.weightx = 3.0;
                    gridBagConstraints2.weighty = 30.0;
                    gridBagConstraints2.fill = GridBagConstraints.BOTH;

                    gridLayoutPanel.add(videoNameLabel, gridBagConstraints2);


                    GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
                    gridBagConstraints3.insets = new Insets(5, 5, 5, 5);
                    gridBagConstraints3.gridy = rowCount;
                    gridBagConstraints3.gridx = 1;
                    gridBagConstraints3.weightx = 50.0;
                    gridBagConstraints3.weighty = 30.0;
                    gridBagConstraints3.fill = GridBagConstraints.BOTH;

                    gridLayoutPanel.setPreferredSize(new Dimension(gridLayoutPanel.getWidth(), gridLayoutPanel.getHeight() + 100));

                    gridLayoutPanel.add(vfxPanel, gridBagConstraints3);

                    videoPlayerPanel.revalidate();
                    videoPlayerPanel.repaint();

                    gridLayoutPanel.revalidate();
                    gridLayoutPanel.repaint();

                    cardLayoutPanel.revalidate();
                    cardLayoutPanel.repaint();

                    rowCount++;
                });
            }
        }

        void show()
        {
            window.setVisible(true);
        }
}
