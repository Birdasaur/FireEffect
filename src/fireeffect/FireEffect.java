package fireeffect;

import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 *
 * @author phillsm1
 */
public class FireEffect extends Application {

    Canvas canvas;

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(600, 600);
        BorderPane root = new BorderPane(canvas);
        Scene scene = new Scene(root, Color.WHITESMOKE);
        initCanvas();
        primaryStage.setTitle("Classic Fire");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private void initCanvas() {
        //define the width and height of the screen and the buffers
        int screenWidth = 600;
        int screenHeight = 600;

        // Y-coordinate first because we use horizontal scanlines
        int[][] fire = new int[screenHeight][screenWidth];  //this buffer will contain the fire
        Color[] palette; //this will contain the color palette
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        palette = generatePalette(256);

        Task fireTask = new Task() {
            @Override
            protected Void call() throws Exception {
                long counter = 0;
                Random rand = new Random();
                //random latch
                boolean latch = true;
                
                //start the loop (one frame per loop)
                while(!this.isCancelled() && !this.isDone()) {
                    if(latch) {
                        //randomize the bottom row of the fire buffer
                        for (int x = 0; x < screenWidth; x++) {
                            fire[screenHeight - 1][x] = Math.abs(32768 + rand.nextInt(65536)) % 256;
                        }
//                        latch = false;
                    }
                    //do the fire calculations for every pixel, from top to bottom
                    for (int y = 0; y < screenHeight - 1; y++) {
                        for (int x = 0; x < screenWidth; x++) {
                            fire[y][x]
                                    = ((fire[(y + 1) % screenHeight][(x - 1 + screenWidth) % screenWidth]
                                    + fire[(y + 2) % screenHeight][(x) % screenWidth]
                                    + fire[(y + 1) % screenHeight][(x + 1) % screenWidth]
                                    + fire[(y + 3) % screenHeight][(x) % screenWidth])
                                    * 128) / 513;
                            
                        }
                    }
                    System.out.println(counter++);    
                    Thread.sleep(33);
                }
                return null;
            }
        };
        Thread thread = new Thread(fireTask);
        thread.setDaemon(true);
        thread.start();
        
        AnimationTimer at = new AnimationTimer() {
            public long lastTimerCall = 0;
            private final long NANOS_PER_MILLI = 1000000; //nanoseconds in a millisecond
            private final long ANIMATION_DELAY = 16 * NANOS_PER_MILLI;   

            @Override
            public void handle(long now) {
                if(now > lastTimerCall + ANIMATION_DELAY) {
                    //set the drawing buffer to the fire buffer, using the palette colors
                    for (int y = 0; y < screenHeight; y++) {
                        for (int x = 0; x < screenWidth; x++) {
                            //buffer[y][x] = palette[fire[y][x]];
                            pw.setColor(x, y, palette[fire[y][x]]);
                        }
                    }
                    lastTimerCall = now;    //update for the next animation
                }
            }
        };
        at.start();
    }
   
    private Color[] generatePalette(int max ) {
        Color [] pal = new Color[max];
        //generate the palette
        for (int x = 0; x < max; x++) {
            //HSLtoRGB is used to generate colors:
            //Hue goes from 0 to 85: red to yellow
            //Saturation is always the maximum: 255
            //Lightness is 0..255 for x=0..128, and 255 for x=128..255
            //color = HSLtoRGB(ColorHSL(x / 3, 255, std::min(255, x * 2)));
            //set the palette to the calculated RGB value
            //palette[x] = RGBtoINT();
            //double brightness = Math.min(255, x*2) / 255.0;
            //pal[x] = Color.hsb(x / 3.0, 1.0, brightness , 1);
            pal[x] = Color.rgb(x, x, x);
        }        
        return pal;
    }
    private int rgbToInt(Color colorRGB) {
      return 65536 * (int)(colorRGB.getRed()*255) + 256 * (int)(colorRGB.getGreen()*255) + (int)(colorRGB.getBlue()*255);
    }

    Color INTtoRGB(int colorINT) {
      return new Color(
        (colorINT / 65536) % 256, 
        (colorINT / 256) % 256,
        colorINT % 256,
        1.0);
    }    

//    private void writePixels(GraphicsContext gc) 
//    {
//        // Define properties of the Image
//        int spacing = 5;
//        int imageWidth = 300;
//        int imageHeight = 100;
//        int rows = imageHeight/(RECT_HEIGHT + spacing);
//        int columns = imageWidth/(RECT_WIDTH + spacing);
// 
//        // Get the Pixels
//        byte[] pixels = this.getPixelsData();
//         
//        // Create the PixelWriter
//        PixelWriter pixelWriter = gc.getPixelWriter();
//         
//        // Define the PixelFormat
//        PixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteRgbInstance();
//         
//        // Write the pixels to the canvas
//        for (int y = 0; y < rows; y++) 
//        {
//            for (int x = 0; x < columns; x++) 
//            {
//                int xPos = 50 + x * (RECT_WIDTH + spacing);
//                int yPos = 50 + y * (RECT_HEIGHT + spacing);
//                pixelWriter.setPixels(xPos, yPos, RECT_WIDTH, RECT_HEIGHT,
//                        pixelFormat, pixels, 0, RECT_WIDTH * 3);
//            }
//        }    
//    }
//    private byte[] getPixelsData() 
//    {
//        // Create the Array
//        byte[] pixels = new byte[RECT_WIDTH * RECT_HEIGHT * 3];
//        // Set the ration
//        double ratio = 1.0 * RECT_HEIGHT/RECT_WIDTH;
//        // Generate pixel data
//        for (int y = 0; y < RECT_HEIGHT; y++) 
//        {
//            for (int x = 0; x < RECT_WIDTH; x++) 
//            {
//                int i = y * RECT_WIDTH * 3 + x * 3;
//                if (x <= y/ratio) 
//                {
//                    pixels[i] = -1;
//                    pixels[i+1] = 1;
//                    pixels[i+2] = 0;
//                } 
//                else
//                {
//                    pixels[i] = 1;
//                    pixels[i+1] = 1;
//                    pixels[i+2] = 0;
//                }
//            }
//        }
//
//        // Return the Pixels
//        return pixels;
//    }     
}
