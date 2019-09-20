package fireeffect;
import java.nio.ByteBuffer;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
/**
 *
 * @author phillsm1
 */
public class ResizableFireEffect extends Application {

    SimpleBooleanProperty classic = new SimpleBooleanProperty(true);
    SimpleBooleanProperty resizing = new SimpleBooleanProperty(false);
    SimpleBooleanProperty computing = new SimpleBooleanProperty(false);
    SimpleBooleanProperty rendering = new SimpleBooleanProperty(false);
    
    Canvas canvas;
    int[] paletteAsInts; //this will contain the color palette
    int shift1, shift2, shift3;  //cheesy way to use bit shifting to make waves

    int screenWidth, screenHeight;
    int minWidth = 10, minHeight = 10;
    
    int[] fire; //this buffer will contain the fire
    int[] fireBuf; //double buffer
    int[] bottomRow; //seeds of flames randomly generated
    WritableImage writableImage; //image to facilitate concurrent write/reads
    PixelWriter pwBuffer;
    PixelReader prBuffer;
    GraphicsContext gc;
    PixelWriter pw;
    
    
    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(600, 600);
        VBox vb = new VBox(canvas);
        vb.minWidth(minWidth);
        vb.minHeight(minHeight);
        canvas.widthProperty().bind(vb.widthProperty());
        canvas.heightProperty().bind(vb.heightProperty());
        BorderPane root = new BorderPane(vb);
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
    private void reallocateBuffers(int width, int height) {
        resizing.set(true);
//        //Gotta wait for any rendering threads to stop.
//        while(rendering.get()) {
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException ex) {
//                Logger.getLogger(ResizableFireEffect.class.getName()).log(Level.SEVERE, null, ex);
//                //@TODO SMP watchdog logic needed
//            }
//        }
        // Y-coordinate first because we use horizontal scanlines
        screenWidth = width < minWidth ? minWidth : width;
        screenHeight = height < minHeight ? minHeight : height;
        fire = new int[screenHeight * screenWidth];  //this buffer will contain the fire
        fireBuf = new int[screenHeight * screenWidth];
        bottomRow = new int[screenWidth];
        writableImage = new WritableImage(screenWidth, screenHeight);
        pwBuffer = writableImage.getPixelWriter();
        prBuffer = writableImage.getPixelReader();
        gc = canvas.getGraphicsContext2D();
        pw = gc.getPixelWriter();
        resizing.set(false);
    }

    private void initCanvas() {
        WritablePixelFormat<IntBuffer> pixelFormat = WritablePixelFormat.getIntArgbInstance();

        //define the width and height of the screen and the buffers
        reallocateBuffers(600, 600);
        canvas.widthProperty().addListener(event -> {
            if(canvas.getWidth() > 0 && canvas.getHeight() > 0)
                reallocateBuffers((int)canvas.getWidth(), (int)canvas.getHeight());
        });
        canvas.heightProperty().addListener(event ->{
            if(canvas.getWidth() > 0 && canvas.getHeight() > 0)
                reallocateBuffers((int)canvas.getWidth(), (int)canvas.getHeight());
        });
        paletteAsInts = generateArgbPalette(256);

        Task fireTask = new Task() {
            @Override
            protected Void call() throws Exception {
                Random rand = new Random();
                long startTime = 0;
                long elapseTime = 0;
                //start the loop (one frame per loop)
                int fireStartHeight, a, b, row, index, pixel;
                int skipY = 1;
                int skipX = 1;
                while(!this.isCancelled() && !this.isDone()) {
                    if(!resizing.get()) {
                        computing.set(true);
                        try {
                            // Start stop watch
                            startTime = System.currentTimeMillis();
                            fireStartHeight = (screenHeight - 1) * screenWidth;
                            //randomize the bottom row of the fire buffer
                            Arrays.parallelSetAll(bottomRow, value -> Math.abs(32768 + rand.nextInt(65536)) % 256);
                            System.arraycopy(bottomRow, 0, fire, fireStartHeight, screenWidth);

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
                        } catch (Exception ex) {
                            System.out.println("worker thread burp...");
                        }
                        
                        computing.set(false);
                    }
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
                    lastTimerCall = now;    //update for the next animation
                    if(!resizing.get()) {
                        rendering.set(true);
                        startTime = System.nanoTime();
                        try {
                            pw.setPixels(0, 0, screenWidth, screenHeight, prBuffer, 0, 0);
                        } catch (Exception ex) {
                            System.out.println("animation timer burp...");
                        }                        
                        elapseTime = (System.nanoTime() - startTime)/1e6;
                        System.out.println("UI Render thread takes : " + elapseTime + "ms");
                        rendering.set(false);
                    }
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

    static Color INTtoRGB(int colorINT) {
      return new Color(
        (colorINT / 65536) % 256, 
        (colorINT / 256) % 256,
        colorINT % 256,
        1.0);
    }    
}    