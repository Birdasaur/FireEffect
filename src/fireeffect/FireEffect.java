package fireeffect;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.IntBuffer;
import java.util.Random;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
/**
 * Demo of fire effects with standard Java 8 using a writable image.
 * A 600 x 600 canvas, worker thread with a 17ms sleep and animation timer at 16ms delay.
 * @author phillsm1
 * @author carldea
 */
public class FireEffect extends Application {

    SimpleBooleanProperty classic = new SimpleBooleanProperty(true);
    Canvas canvas;
    int[] paletteAsInts; //this will contain the color palette
    int shift1, shift2, shift3;
    SimpleLongProperty workerTimes = new SimpleLongProperty(0);
    long workTimesMillis = 0;

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
        
        ChoiceBox<Integer> shift1ChoiceBox = new ChoiceBox<>(
            FXCollections.observableArrayList(
                -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        shift1ChoiceBox.setOnAction(event -> shift1 = shift1ChoiceBox.getValue());
        shift1ChoiceBox.getSelectionModel().select(17);
        
        ChoiceBox<Integer> shift2ChoiceBox = new ChoiceBox<>(
            FXCollections.observableArrayList(
                -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        shift2ChoiceBox.setOnAction(event -> shift2 = shift2ChoiceBox.getValue());
        shift2ChoiceBox.getSelectionModel().select(9);
        ChoiceBox<Integer> shift3ChoiceBox = new ChoiceBox<>(
            FXCollections.observableArrayList(
                -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        shift3ChoiceBox.setOnAction(event -> shift3 = shift3ChoiceBox.getValue());
        shift3ChoiceBox.getSelectionModel().select(1);
        
        HBox toggleBox = new HBox(10, classicRB, wavesRB, 
            new Label("Wave Shift 1"), shift1ChoiceBox, 
            new Label("Wave Shift 2"), shift2ChoiceBox, 
            new Label("Wave Shift 3"), shift3ChoiceBox);
        toggleBox.setPadding(new Insets(5));
        root.setTop(toggleBox);
        Scene scene = new Scene(root, Color.BLACK);
        initCanvas();
        workerTimes.addListener( listener -> canvas.getGraphicsContext2D().strokeText("Worker time spent: " + workerTimes.get() + "ms", 10, 10));
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
        //int[] bottomRow = new int[screenWidth];
        
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
                long startTime = 0;
                long elapseTime = 0;
                int fireStartHeight = (screenHeight - 1) * screenWidth;
                //start the loop (one frame per loop)
                while(!this.isCancelled() && !this.isDone()) {
                    // Start stop watch
                    startTime = System.currentTimeMillis();

                    //randomize the bottom row of the fire buffer
//                    Arrays.parallelSetAll(bottomRow, value -> Math.abs(32768 + rand.nextInt(65536)) % 256);
//                    System.arraycopy(bottomRow, 0, fire, fireStartHeight, screenWidth);
                    for(int i=fireStartHeight; i<fireStartHeight+screenWidth; i++) {
                        fire[i] = Math.abs(32768 + rand.nextInt(65536)) % 256;
                    }
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
//                    System.out.println("Worker thread takes : " + elapseTime + "ms");
                    workTimesMillis = elapseTime;

                    Thread.sleep(17);
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

                    gc.setFill(Color.WHITE);
                    gc.fillText("Worker time spent: " + workTimesMillis + "ms", 10, 15);

                    //System.out.println("UI Render thread takes : " + elapseTime + "ms");
                    elapseTime = (System.nanoTime() - startTime)/1e6;
                    gc.fillText("UI Render thread takes : " + elapseTime + "ms", 10, 30);
                }
            }
        };
        at.start();
    }
    private int getPaletteValue(int pixelIndex) {
        if(classic.get())  
            return paletteAsInts[pixelIndex];
        int value = 0;
        if(shift1 > -1)
            value |= paletteAsInts[pixelIndex] << shift1; 
        if(shift2 > -1)
            value |= paletteAsInts[pixelIndex] << shift2; 
        if(shift3 > -1)
            value |= paletteAsInts[pixelIndex] << shift3;
        return value;
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

//    static Color INTtoRGB(int colorINT) {
//      return new Color(
//        (colorINT / 65536) % 256,
//        (colorINT / 256) % 256,
//        colorINT % 256,
//        1.0);
//    }
}    