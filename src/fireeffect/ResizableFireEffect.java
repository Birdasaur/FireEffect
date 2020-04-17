package fireeffect;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * TODO fix race condition. Make the reallocation and effect more seemless.
 * @deprecated WIP or rework.
 * @author carldea
 */
public class ResizableFireEffect extends Application {

    boolean classic = true;
    volatile boolean resizing = false;

    int shift1, shift2, shift3;  //cheesy way to use bit shifting to make waves

    int screenWidth, screenHeight; // The canvas dimensions
    final int MIN_WIDTH = 10, MIN_HEIGHT = 10; // minimum size the canvas can be
    final long DEFAULT_WORKER_SLEEP = 25; // milliseconds
    Canvas canvas;      // main render surface displayed to the user
    int[] paletteAsInts; //this will contain a 32 bit (integer) array of colors for the palette

    int[] fire;         //this buffer will contain the fire
    int[] fireBuf;      //double buffer
    int[] bottomRow;    //seeds of flames randomly generated

    // create a worker queue, basically a consumer producer pattern
    Queue<WritableImage> workerQueue = new ConcurrentLinkedQueue<>();
    Queue<WritableImage> renderQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(Stage primaryStage) {

        // Create center area
        StackPane stackPane = new StackPane();
        canvas = new Canvas();
        stackPane.getChildren().add(canvas);

        // Create root pane
        BorderPane root = new BorderPane(stackPane);

        // Create top are controls
        RadioButton classicRB = new RadioButton("Classic flame");
        classicRB.setSelected(true);
        classicRB.selectedProperty().addListener(event -> classic = classicRB.isSelected());

        RadioButton wavesRB = new RadioButton("Waves of Fire");
        ToggleGroup tg = new ToggleGroup();
        tg.getToggles().addAll(classicRB, wavesRB);
        List<Integer> integerList = IntStream.range(-1, 17).boxed().collect(Collectors.toList());
        ChoiceBox<Integer> shift1ChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(integerList));

        shift1ChoiceBox.setOnAction(event -> shift1 = shift1ChoiceBox.getValue());
        shift1ChoiceBox.getSelectionModel().select(17);
        
        ChoiceBox<Integer> shift2ChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(integerList));
        shift2ChoiceBox.setOnAction(event -> shift2 = shift2ChoiceBox.getValue());
        shift2ChoiceBox.getSelectionModel().select(9);
        ChoiceBox<Integer> shift3ChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(integerList));

        shift3ChoiceBox.setOnAction(event -> shift3 = shift3ChoiceBox.getValue());
        shift3ChoiceBox.getSelectionModel().select(1);

        // Create top controls area
        HBox toggleBox = new HBox(10, classicRB, wavesRB, 
            new Label("Wave Shift 1"), shift1ChoiceBox, 
            new Label("Wave Shift 2"), shift2ChoiceBox, 
            new Label("Wave Shift 3"), shift3ChoiceBox);
        toggleBox.setPadding(new Insets(5));
        root.setTop(toggleBox);

        // Once things are shown (scene) the size of the canvas can be created.
        primaryStage.setOnShown(winEvents -> initCanvas(primaryStage));

        // Create the default size of the scene (view port inside stage)
        Scene scene = new Scene(root, 800, 600, Color.BLACK);

        // Canvas width is the current stage's width
        canvas.widthProperty().bind(scene.widthProperty());

        // Canvas is the scene's height minus the top area.
        canvas.heightProperty()
                .bind(scene.heightProperty()
                .subtract(toggleBox.heightProperty()));


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
        resizing = true;

        // Y-coordinate first because we use horizontal scanlines
        screenWidth = width < MIN_WIDTH ? MIN_WIDTH : width;
        screenHeight = height < MIN_HEIGHT ? MIN_HEIGHT : height;

        // Remove any buffered writable images
        int numberOfBuffers = workerQueue.size() + renderQueue.size();
        System.out.println("numberOfBuffers: " + numberOfBuffers);
        workerQueue.clear();
        renderQueue.clear();
        for (int i=0; i<numberOfBuffers; i++) {
            workerQueue.add(new WritableImage(screenWidth, screenHeight));
        }

        // Reallocate convolution pixel arrays
        fire = new int[screenHeight * screenWidth];  //this buffer will contain the fire
        fireBuf = new int[screenHeight * screenWidth];
        bottomRow = new int[screenWidth];

        resizing = false;
    }

    /**
     * This is called after the Stage has been shown to determine the size of the canvas.
     * @param primaryStage
     */
    private void initCanvas(Stage primaryStage) {
        // Change the dimensions of the arrays based on the render canvas area.
        reallocateBuffers((int) canvas.getWidth(), (int) canvas.getHeight());

        // Add a (one) buffer for the worker thread to fill with pixels.
        workerQueue.add(new WritableImage(screenWidth, screenHeight));
        workerQueue.add(new WritableImage(screenWidth, screenHeight));
        workerQueue.add(new WritableImage(screenWidth, screenHeight));

        // Generate a palette of random hsb based colors.
        paletteAsInts = generateArgbPalette(256);

        // Worker thread task to perform convolution (using CPU one thread)
        Task fireTask = new Task() {
            @Override
            protected Void call() throws Exception {
                Random rand = new Random();
                long startTime = 0;
                long markTime = 0;
                long elapseTime = 0;
                //start the loop (one frame per loop)
                int fireStartHeight;
                int a, b;
                int row, pixel;
                // argb writable surface as opposed to a rgba.
                WritablePixelFormat<IntBuffer> pixelFormat = WritablePixelFormat.getIntArgbInstance();

                while(!this.isCancelled() && !this.isDone()) {
                    if (resizing) {
                        continue; // if still resizing ignore calculations
                    }

                    // Start stop watch
                    startTime = System.currentTimeMillis();

                    // Randomize the bottom row of the fire buffer
                    Arrays.parallelSetAll(bottomRow, (int operand) -> Math.abs(32768 + rand.nextInt(65536)) % 256);
                    fireStartHeight = (screenHeight - 1) * screenWidth;
                    System.arraycopy(bottomRow, 0, fire, fireStartHeight, screenWidth);

                    markTime = System.currentTimeMillis();

                    // 3x3 convolution of pixels (height minus one)
                    for (int y = 0; y < screenHeight - 1; y++) {
                        a = (y + 1) % screenHeight * screenWidth;
                        row = y * screenWidth;
                        for (int x = 0; x < screenWidth; x++) {
                            b = x % screenWidth;
                            pixel = fire[row + x]
                                  = ((fire[a + ((x - 1 + screenWidth) % screenWidth)]
                                    + fire[((y + 2) % screenHeight) * screenWidth + b]
                                    + fire[a + ((x + 1) % screenWidth)]
                                    + fire[((y + 3) % screenHeight * screenWidth) + b])
                                    * 128) / 513;
                            fireBuf[row + x] = getPaletteValue(pixel);
                        }
                    }

                    elapseTime = System.currentTimeMillis() - markTime;
                    System.out.println("Convolution takes : " + elapseTime + "ms");

                    try {
                        WritableImage writableImage = workerQueue.poll();
                        if (writableImage != null){
                            PixelWriter pwBuffer = writableImage.getPixelWriter();
                            if (isSameDimensions(writableImage)) {
                                System.out.println("1 [same size] Worker sends to render thread");
                                pwBuffer.setPixels(0, 0, screenWidth, screenHeight, pixelFormat, fireBuf, 0, screenWidth);
                                renderQueue.add(writableImage);
                                System.out.println("2 [same size] Worker sends to render thread");
                            } else {
                                // add
                                System.out.println("1 [invalid size] add to Worker queue");
                                workerQueue.add(writableImage);
                                reallocateBuffers(screenWidth, screenHeight);
                                System.out.println("2 [invalid size] add to Worker queue");
                            }
                        } else {
                            System.out.println("Waiting for renderer to put something in work queue.");
                            continue;
                        }
                    } catch (Throwable th) {
                        // When this happens setPixels() array is out of bounds.
                        th.printStackTrace();
                    }
                    elapseTime = System.currentTimeMillis() - startTime;
                    System.out.println("Convolution + setPixels takes : " + elapseTime + "ms");

                    Thread.sleep(DEFAULT_WORKER_SLEEP);
                }
                return null;
            }
        };

        // @TODO possibly make an executor thread pool to distribute work
        // to put more on the render queue to appear faster
        Thread thread = new Thread(fireTask);
        thread.setDaemon(true);
        thread.start();

        // Create an animation UI thread to make fast copies from the back buffer
        // to the main canvas area. This should try to do things less than or equal to
        // the FPS. eg. 60 FPS is around 16ms a frame.
        AnimationTimer at = new AnimationTimer() {
            long lastTimerCall = 0;
            final long NANOS_PER_MILLI = 1000000; //nanoseconds in a millisecond
            final long ANIMATION_DELAY = 16 * NANOS_PER_MILLI;
            double startTime;
            double elapseTime;

            @Override
            public void handle(long now) {
                if(now > lastTimerCall + ANIMATION_DELAY) {
                    lastTimerCall = now;    //update for the next animation

                    if(!resizing) {
                        startTime = System.nanoTime();
                        try {
                            PixelWriter pw = canvas.getGraphicsContext2D().getPixelWriter();
                            System.out.println("Num items to render: " + renderQueue.size());

                            // grab work to do
                            WritableImage fireImageFrame = renderQueue.poll();

                            // if their is work to do
                            if (fireImageFrame != null) {
                                if (isSameDimensions(fireImageFrame)) {
                                    // fast copy (uses the GPU)
                                    pw.setPixels(0, 0, screenWidth, screenHeight, fireImageFrame.getPixelReader(), 0, 0);
                                }
                                workerQueue.add(fireImageFrame);
                            } else {
                                System.out.println("No work to draw");
                                return;
                            }
                        } catch (Throwable th) {
                            // should never happen
                            th.printStackTrace();
                            System.out.println("animation timer burp...");
                        }                        
                        elapseTime = (System.nanoTime() - startTime)/1e6;
                        System.out.println("UI Render thread takes : " + elapseTime + "ms");

                    }
                }
            }
        };

        at.start();

        // Debounce the change of width and height of the window resizing.
        DebounceDispatcher resizeListener = new DebounceDispatcher(500)
                .onAction(() -> {
                    // This action is run on the JavaFX ui thread.
                    System.out.println(Thread.currentThread().getName() + " reallocate() called.");

                    // stop animation timer
                    at.stop();

                    int w = (int) canvas.getWidth();
                    int h = (int) canvas.getHeight();

                    // reallocate buffers, palettes, etc.
                    reallocateBuffers(w, h);

                    // restart animation timer
                    at.start();

                    System.out.println(String.format("Reallocate arrays [%s by %s]", w, h));
                });

        // Add debounce dispatcher to listen for width and hight changes.
        primaryStage.widthProperty().addListener(resizeListener);
        primaryStage.heightProperty().addListener(resizeListener);
    }

    /**
     * Just in case the dimension are different caller
     * can avoid setPixels() It kills the running thread.
     * @param fireImageFrame
     * @return
     */
    private boolean isSameDimensions(WritableImage fireImageFrame) {
        return (int) fireImageFrame.getWidth() == screenWidth &&
               (int) fireImageFrame.getHeight() == screenHeight;
    }

    private int getPaletteValue(int pixelIndex) {
        if(classic)
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