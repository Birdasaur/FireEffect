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
import static javafx.application.Application.launch;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
/**
 *
 * @author phillsm1
 */
public class FireEffect extends Application {

    SimpleBooleanProperty classic = new SimpleBooleanProperty(true);
    Canvas canvas;
    int[] paletteAsInts; //this will contain the color palette

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(600, 600);
        BorderPane root = new BorderPane(canvas);
        RadioButton classicRB = new RadioButton("Classic flame");
        classicRB.setSelected(true);
        classic.bind(classicRB.selectedProperty());
        RadioButton wavesRB = new RadioButton("Waves of Fire");
        ToggleGroup tg = new ToggleGroup();
        tg.getToggles().addAll(classicRB, wavesRB);
        HBox toggleBox = new HBox(10, classicRB, wavesRB);
        root.setTop(toggleBox);
        Scene scene = new Scene(root, Color.BLACK);
        initCanvas();
        primaryStage.setTitle("FireEffect");
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
        
//        int[] paletteAsInts; //this will contain the color palette
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
//                    System.arraycopy(bottomRow, 0, fireBuf, fireStartHeight, screenWidth);

                    int a, b, row, index, pixel;
                    
                    for (int y = 0; y < screenHeight - 1; y++) {
                        for (int x = 0; x < screenWidth; x++) {
                            a = (y + 1) % screenHeight * screenWidth;
                            b = x % screenWidth;
                            row = y * screenWidth;
                            index = row + x;
                            pixel = fire[index]
                                    = ((fire[a + ((x - 1 + screenWidth) % screenWidth)]
                                    + fire[((y + 2) % screenHeight) * screenWidth + b]
                                    + fire[a + ((x + 1) % screenWidth)]
                                    + fire[((y + 3) % screenHeight * screenWidth) + b])
                                    * 128) / 513;
                            fireBuf[index] = getPaletteValue(pixel);
                        }
                    }

                    pwBuffer.setPixels(0, 0, screenWidth, screenHeight, pixelFormat, fireBuf, 0, screenWidth);
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
    private int getPaletteValue(int pixelIndex) {
        if(classic.get())  
            return paletteAsInts[pixelIndex];
        else {
            return  
                paletteAsInts[pixelIndex] << 16 |
                paletteAsInts[pixelIndex] << 8 | 
                paletteAsInts[pixelIndex];
        }
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
            double brightness = Math.min(255, x*2) / 255.0;
            Color color = Color.hsb(x / 3.0, 1.0, brightness , 1);
            pal[x] = rgbToIntArgb(color);            
        }
        return pal;
    }

    private static int rgbToIntArgb(Color colorRGB) {
      return (int)(colorRGB.getOpacity()*255) << 24 |
             (int)(colorRGB.getRed()    *255) << 16 | 
             (int)(colorRGB.getGreen()  *255) <<  8 | 
             (int)(colorRGB.getBlue()   *255);
    }    

    static Color INTtoRGB(int colorINT) {
      return new Color(
        (colorINT / 65536) % 256, 
        (colorINT / 256) % 256,
        colorINT % 256,
        1.0);
    }    
}    