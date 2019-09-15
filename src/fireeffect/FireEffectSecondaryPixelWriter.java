package fireeffect;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author phillsm1
 */
public class FireEffectSecondaryPixelWriter extends Application {

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
        int[] fire = new int[screenHeight * screenWidth];  //this buffer will contain the fire
        int[] fireBuf = new int[screenHeight * screenWidth];

        int[] bottomRow = new int[screenWidth];
        int[] paletteAsInts; //this will contain the color palette
        WritableImage writableImage = new WritableImage(screenWidth, screenHeight);
        PixelWriter pwBuffer = writableImage.getPixelWriter();
        PixelReader prBuffer = writableImage.getPixelReader();
        WritablePixelFormat<IntBuffer> pixelFormat = WritablePixelFormat.getIntArgbInstance();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        paletteAsInts = generateArgbPalette(256);

        Task fireTask = new Task() {
            @Override
            protected Void call() throws Exception {
                Random rand = new Random();
                //random latch
                boolean latch = true;
                long startTime = 0;
                long elapseTime = 0;
                int fireStartHeight = (screenHeight - 1) * screenWidth;
                //start the loop (one frame per loop)
                while(!this.isCancelled() && !this.isDone()) {
                    // Start stop watch
                    startTime = System.currentTimeMillis();

                    //randomize the bottom row of the fire buffer
                    Arrays.parallelSetAll(bottomRow, value -> Math.abs(32768 + rand.nextInt(65536)) % 256);
                    System.arraycopy(bottomRow, 0, fire, fireStartHeight, screenWidth);

                    int a, b, row;
                    for (int y = 0; y < screenHeight - 1; y++) {
                        for (int x = 0; x < screenWidth; x++) {
                            a = (y + 1) % screenHeight * screenWidth;
                            b = (x) % screenWidth;
                            row = y * screenWidth;
                            fire[row + x]
                                    = ((fire[a + ((x - 1 + screenWidth) % screenWidth)]
                                    + fire[((y + 2) % screenHeight) * screenWidth + b]
                                    + fire[a + ((x + 1) % screenWidth)]
                                    + fire[((y + 3) % screenHeight * screenWidth) + b])
                                    * 128) / 513;

                        }
                    }
/////////////////////////////////////////////////////////////////////////////
// Not sure why below is slower than above.
//                    int heightMinusOne = (screenHeight-1) * screenWidth;
//                    for (int z = 0; z < heightMinusOne; z++) {
//                            int y = z/screenWidth;
//                            int a = (y + 1) % screenHeight * screenWidth;
//                            int b = z % screenWidth;
//                            //int row = z/screenWidth * screenWidth;
//                            fire[z]
//                                    = ((fire[a + ((b - 1 + screenWidth) % screenWidth)]
//                                    + fire[((y + 2) % screenHeight) * screenWidth + b]
//                                    + fire[a + ((b + 1) % screenWidth)]
//                                    + fire[((y + 3) % screenHeight * screenWidth) + b])
//                                    * 128) / 513;
//
//                    }

                    int c, pAsInt, r2, g2, b2, newPixel;
                    for (int z = 0; z < fire.length; z++) {
                        c = fire[z];
                        pAsInt = paletteAsInts[c];
                        r2 = (pAsInt << 16);
                        g2 = (pAsInt << 8);
                        b2 = pAsInt;
                        newPixel = (0xFF<<24) | r2 | g2 | b2;
                        //fireBuf[z] = newPixel;

                        // Not sure if there is an issue with the timing when render thread
                        // is waiting when the system.out is slowing the worker thread the output is different.
                        //System.out.println("pAsInt=" + pAsInt + " newPixel=" + newPixel + " " + INTtoRGB(newPixel));

                        pwBuffer.setArgb(z%screenWidth, (z/screenWidth), newPixel);

                        //pwBuffer.setColor(z%screenWidth, (z/screenWidth), INTtoRGB(pAsInt));
                    }

//                    synchronized (lock) {
//                        pwBuffer.setPixels(0, 0, screenWidth, screenHeight-1, pixelFormat, fireBuf, 0, fireBuf.length);
//                    }
//
//                    pwBuffer.setPixels(0, 0, screenWidth, screenHeight, pixelFormat, fire2, 0, fire2.length);

                    elapseTime = System.currentTimeMillis() - startTime;
                    System.out.println("Worker thread takes : " + elapseTime + "ms");
                    //System.out.println(counter++);
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
            private double startTime;
            private double elapseTime;
            @Override
            public void handle(long now) {
                if(now > lastTimerCall + ANIMATION_DELAY) {

                    startTime = System.nanoTime();
                    pw.setPixels(0, 0, screenWidth, screenHeight, prBuffer, 0, 0);
                    lastTimerCall = now;    //update for the next animation
                    elapseTime = (System.nanoTime() - startTime)/1e6;
                    System.out.println("UI Render thread takes : " + elapseTime + "ms");
                }
            }
        };
        at.start();
    }

    private int[] generateArgbPalette(int max ) {
        int [] pal = new int[max];
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
            // 65536 * r + 256 * g + b;
            //pal[x] = rgbToInt(Color.rgb(x, x, x));
            double brightness = Math.min(255, x*2) / 255.0;
            Color color = Color.hsb(x / 3.0, 1.0, brightness , 1);
            pal[x] = rgbToInt(color);
            //System.out.println("pal[x] " + color + " int is " + pal[x]);
        }
        //if (true) throw new NullPointerException();
        return pal;
    }
    private static int rgbToInt(Color colorRGB) {
      return 65536 * (int)(colorRGB.getRed()*255) + 256 * (int)(colorRGB.getGreen()*255) + (int)(colorRGB.getBlue()*255);
    }

    static Color INTtoRGB(int colorINT) {
      return new Color(
        (colorINT / 65536) % 256, 
        (colorINT / 256) % 256,
        colorINT % 256,
        1.0);
    }    
}
