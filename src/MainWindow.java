import javax.swing.*;

public class MainWindow {

    private JFrame window;
    JFrame frame = new JFrame();

        public MainWindow(){
            window = new JFrame();
            window.setTitle("Video Viewer");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);
            window.setSize(800, 600);
            window.setLocationRelativeTo(null);
        }

        void show()
        {
            window.setVisible(true);
        }
}
