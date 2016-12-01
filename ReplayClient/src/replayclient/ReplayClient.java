/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replayclient;
import drawclient.DrawClient.Point;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;


public class ReplayClient extends JPanel {

    private final ArrayList<ArrayList<ArrayList<Point>>> directory = new ArrayList<>();

    private final Color color;
    private int page;
    private final JScrollPane scrollPanel;
    private final JPanel mainPanel = new JPanel(new GridBagLayout());
    private ObjectInputStream ois;
    private JButton playPauseButton;
    private final JButton openFileButton;
    private int state = 0;//zero for pause 1 for playing
    private Queue<Command> order, orderFinal;
    private Thread playing;
    private Clip audio;
    private boolean initialized = false;
    private Image play,pause;
    private JProgressBar slider;
    private Timer sliderTimer;
    private final Command lockOrder,lockDir;
    ReplayClient() throws IOException
    {
        
        page = 0;
        color = Color.BLACK;
        playPauseButton = new JButton();
        openFileButton = new JButton();
        //init first page
        directory.add(new ArrayList<>());
        order = new LinkedList<>();
        this.setBackground(Color.WHITE);
        playPauseButton.setEnabled(false);
        slider = new JProgressBar(0,0);
        slider.setStringPainted(true);
        slider.setString("No file loaded!");
        sliderTimer = new Timer(1000,new UpdateSlider());
        lockOrder = new Command("",0);
        lockDir = new Command("",0);
        playPauseButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                //if paused then play
                if(state == 0)
                {
                    
                    audio.start();  
                    sliderTimer.start();
                    state = 1;
                    playPauseButton.setIcon(new ImageIcon(pause));
                    
                    if(!initialized)
                    {
                        playing = new Thread(new PlayFile());
                        playing.start();
                        initialized = true;
                    }
                    
                }
                else //if playing then pause
                {
                    audio.stop();
                    sliderTimer.stop();
                    state = 0;
                    playPauseButton.setIcon(new ImageIcon(play));
                }
            }
        });

        openFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e) 
            {
                JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
                chooser.setFileFilter(new FileNameExtensionFilter(null,"zip"));
                int retrieval = chooser.showOpenDialog(null);
                if (retrieval == JFileChooser.APPROVE_OPTION) 
                {
                    try 
                    {
                        BufferedOutputStream dest;
                        FileInputStream fis = new 
                          FileInputStream(chooser.getSelectedFile());
                        
                        ZipInputStream zis = new 
                            ZipInputStream(new BufferedInputStream(fis));
                        ZipEntry entry;
                        
                        while((entry = zis.getNextEntry()) != null) {
                            int count;
                            byte data[] = new byte[1024];
                            // write the files to the disk
                            FileOutputStream fos = new 
                              FileOutputStream(entry.getName());
                            dest = new 
                              BufferedOutputStream(fos, 1024);
                            while ((count = zis.read(data, 0, 1024)) 
                              != -1) {
                               dest.write(data, 0, count);
                            }
                            dest.flush();
                            dest.close();
                         }
                        
                        File tempTxt = new File("temp.txt");
                        File tempAud = new File("temp.wav");
                        
                        AudioInputStream stream;
                        stream = AudioSystem.getAudioInputStream(tempAud);
                        
                        audio = (Clip) AudioSystem.getLine(
                                new DataLine.Info(Clip.class, stream.getFormat()));
                        audio.open(stream);
                        stream.close();
                        
                        
                        
                        ois = new ObjectInputStream(
                                new BufferedInputStream( 
                                        new FileInputStream(tempTxt)));
                        
                        String str;
                        boolean cont = true;
                        while(cont)
                        {
                            String name = null;
                            long time = 0;
                            Point pt = null;
                            str = ois.readUTF();
                            switch(str)
                            {
                                //prev page
                                case "/P":
                                    name = "/P";
                                    time = ois.readLong();
                                    break;
                                //next page
                                case "/N":
                                    name = "/N";
                                    time = ois.readLong();
                                    break;
                                //undo 
                                case "/U":
                                    name = "/U";
                                    time = ois.readLong();
                                    break;
                                //mouse dragged
                                case "/d":
                                    name = "/d";
                                    pt = (Point) ois.readObject();
                                    time = pt.getTime();
                                    break;
                                //mouse pressed
                                case "/p":
                                    name = "/p";
                                    pt = (Point) ois.readObject();
                                    time = pt.getTime();
                                    break;
                                case "/e":
                                    cont = false;
                                    playPauseButton.setEnabled(true);
                                    ois.close();
                                    break;
                                default:
                                    JOptionPane.showMessageDialog(null,"Error reading file!");
                                    cont = false;
                                    order.clear();
                                    break;
                            }
                            if(cont)
                            {
                                Command cmd = new Command(name,time);
                                cmd.setPoint(pt);
                                order.add(cmd);
                            }
                        }
                        
                        slider.setMinimum(0);
                        int secs = (int)audio.getMicrosecondLength()/1000000;
                        slider.setMaximum(secs);
                        slider.addMouseListener(new Seek());
                        orderFinal = new LinkedList(order);
                    } catch (FileNotFoundException ex) {
                        JOptionPane.showMessageDialog(null,"File not found!");
                    } catch (IOException | ClassNotFoundException | LineUnavailableException | UnsupportedAudioFileException ex) {
                        Logger.getLogger(ReplayClient.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
               
        });

        Image img;
        img = ImageIO.read(getClass().getResource("/resources/playButton.png"));
        img = img.getScaledInstance( 50, 50,  java.awt.Image.SCALE_SMOOTH ) ;
        play = img;
        
        playPauseButton.setIcon(new ImageIcon(img));
        playPauseButton.setMargin(new Insets(0, 0, 0, 0));
        playPauseButton.setContentAreaFilled(false);
        playPauseButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        playPauseButton.setHorizontalTextPosition(SwingConstants.CENTER);
        
        img = ImageIO.read(getClass().getResource("/resources/openButton.png"));
        img = img.getScaledInstance( 75, 50,  java.awt.Image.SCALE_SMOOTH ) ;
        openFileButton.setIcon(new ImageIcon(img));
        openFileButton.setMargin(new Insets(0, 0, 0, 0));
        openFileButton.setContentAreaFilled(false);
        openFileButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        openFileButton.setHorizontalTextPosition(SwingConstants.CENTER);
        
        img = ImageIO.read(getClass().getResource("/resources/pauseButton.png"));
        img = img.getScaledInstance( 50, 50,  java.awt.Image.SCALE_SMOOTH ) ;
        pause = img;
        
        
        scrollPanel = new JScrollPane(this);
        JPanel panel = new JPanel(new GridBagLayout());
        JPanel sliderPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        
        
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(10,10,0,0);
        panel.add(openFileButton,c);

        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = 1;
        c.gridy = 0;
        c.insets = new Insets(10,10,0,0);
        panel.add(playPauseButton,c);
        
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(10,10,0,0);
        c.fill = GridBagConstraints.HORIZONTAL;
        sliderPanel.add(slider,c);
        
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = 0;
        c.gridy = 1;
        c.insets = new Insets(10,10,0,0);
        sliderPanel.add(panel,c);
        
        
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        mainPanel.add(sliderPanel,c);
        
        c.insets = new Insets(0,0,0,0);
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        mainPanel.add(scrollPanel,c);
        
        
    }
    private class UpdateSlider implements ActionListener
    {

        @Override
        public void actionPerformed(ActionEvent e) 
        {
            if(audio.isRunning())
            {
                int secs = (int)audio.getMicrosecondLength()/1000000;
                int curSecs = (int)audio.getMicrosecondPosition()/1000000;
                slider.setString(String.format("%d:%02d / %d:%02d", curSecs/60,curSecs%60,secs/60,secs%60));
                slider.setValue(curSecs);
                slider.repaint();
            }
        }
        
    }
    
    private class Seek extends MouseAdapter
    {
        @Override
        public void mousePressed(MouseEvent e)
        {
           int mouseX = e.getX();

           //Computes how far along the mouse is relative to the component width then multiply it by the progress bar's maximum value.
           int sliderVal = (int)Math.round(((double)mouseX / (double)slider.getWidth()) * slider.getMaximum());
           
           audio.setMicrosecondPosition(sliderVal*1000000);
           int secs = (int)audio.getMicrosecondLength()/1000000;
           int curSecs = (int)audio.getMicrosecondPosition()/1000000;
           slider.setString(String.format("%d:%02d / %d:%02d", curSecs/60,curSecs%60,secs/60,secs%60));
           if(sliderVal < slider.getValue())
           {
               synchronized(lockOrder)
               {
               order = new LinkedList(orderFinal);
               }
               synchronized(lockDir)
               {
                directory.clear();
                directory.add(new ArrayList<>());
                page = 0;
                repaint();
               }
               System.out.println("rewinding");
               
           }
           
           slider.setValue(sliderVal);
          
        }
    }
    
    private class PlayFile implements Runnable
    {

        @Override
        public void run() 
        {
            while(true)
            {
                synchronized(lockOrder)
                {
                if(!order.isEmpty())
                {
                    Command cmd = order.peek();   
                    if(audio.getMicrosecondPosition() * 1000 >= cmd.time)
                    {
                        Command execute = order.remove();
                        switch(execute.name)
                        {
                            //prev page
                            case "/P":
                                back();
                                break;
                            //next page
                            case "/N":
                                next();
                                break;
                            //undo 
                            case "/U":
                                undo();
                                break;
                            //mouse dragged
                            case "/d":
                                dragged(execute.point);
                                break;
                            //mouse pressed
                            case "/p":
                                pressed(execute.point);
                                break;
                        }
                    }
                }
                }

            }
        }
    }
    
    
    
    
    private class Command
    {
        private final String name;
        private final long time;
        private Point point;
        
        public void setPoint(Point point)
        {
            this.point = point;
        }
        
        public Point getPoint()
        {
            return point;
        }
        
        public long getTime()
        {
            return time;
        }
        public String getName()
        {
            return name;
        }
        Command(String name, long time)
        {
            this.name = name;
            this.time = time;
        }
    }
    
    private void pressed(Point pt)
    {
        synchronized(lockDir)
        {
            directory.get(page).add(new ArrayList<>());
            directory.get(page).get(directory.get(page).size()-1).add(pt);
            repaint();
        }
    }
    
    private void dragged(Point pt)
    {
        synchronized(lockDir)
        {
            directory.get(page).get(directory.get(page).size()-1).add(pt);
            repaint();
        }
    }
    
    
    @Override
    public Dimension getPreferredSize()
    {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }
    public JPanel getPanel()
    {
        return mainPanel;
    }
    
    //undo last shape
    public void undo()
    {
        synchronized(lockDir)
        {
            if(directory.get(page).size() > 0)
            {
                directory.get(page).remove(directory.get(page).size()-1);
                repaint();
            }
        }
    }
    
    public void back()
    {
        if(page != 0)
        {
            page--;
            repaint();
        }
    }
    
    public void next()
    {
        page++;
        try
        {
            synchronized(lockDir)
            {
                directory.get(page);
            }
        }
        catch(IndexOutOfBoundsException ie)
        {
            synchronized(lockDir)
            {
                directory.add(new ArrayList<>());
            }
        }
    }
    
    
    
    //paint each point as it is received
    @Override
    public void paintComponent(Graphics g)
    {
        //close audio if clip is finished
        if (audio != null && (audio.getMicrosecondLength()== audio.getMicrosecondPosition()))
        {
            state = 1;
            repaint();
            audio.close();
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON); 
        super.paintComponent(g2);
        synchronized(lockDir)
        {
            int l = 0;
            for (ArrayList<Point> ary : directory.get(page)) 
            {
                for(Point point : ary)
                {
                    g2.setPaint(point.getColor());
                    g2.setStroke(new BasicStroke(point.getStroke()));
                    int index = directory.get(page).get(l).indexOf(point);
                    if(index != 0)
                    {
                        g2.drawLine(directory.get(page).get(l).get(index-1).getXPos(),
                                   directory.get(page).get(l).get(index-1).getYPos(), 
                                   point.getXPos(), 
                                   point.getYPos());
                    }
                    //if you press the mouse but dont drag the mouse, still draw a dot
                    else 
                    {
                        g2.drawLine(point.getXPos(),
                                   point.getYPos(), 
                                   point.getXPos(), 
                                   point.getYPos());
                    }
                    
                }
                l++;
            }
        }
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            JFrame window = new JFrame("Drawing Application");
            ReplayClient panel = new ReplayClient();
            window.setContentPane(panel.getPanel());
            Dimension dimen = Toolkit.getDefaultToolkit().getScreenSize();
            window.setPreferredSize(new Dimension(dimen.width/2,dimen.height/2));
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setLocation(100,100);
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
            
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        } catch (IOException ex) {
            Logger.getLogger(ReplayClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
    
}
