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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Fire structure that gets convoluted.
 * @author phillsm1
 * @author cpdea
 */

public class ResizableFireEffect extends Application {
    FireModel fireModel = null;

    boolean classic = true;

    int shift1, shift2, shift3;  //cheesy way to use bit shifting to make waves

    final long DEFAULT_WORKER_SLEEP = 25; // milliseconds

    Canvas canvas;      // main render surface displayed to the user

    // create a worker queue, basically a consumer producer pattern
    Queue<ScreenDimension> workerQueue = new ConcurrentLinkedQueue<>();
    Queue<WritableImage> renderQueue = new ConcurrentLinkedQueue<>();

    private FireModel getFireModel() {
        if (fireModel==null) {
            fireModel = new FireModel();
        }
        return fireModel;
    }

    private void setFireModel(FireModel fireModel) {
        this.fireModel = fireModel;
    }

    @Override
    public void start(Stage primaryStage) {
        // initial fire model
        setFireModel(new FireModel(800, 600));

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
        primaryStage.setOnShown(winEvents -> initCanvas(primaryStage, toggleBox.heightProperty().intValue()));

        // Create the default size of the scene (view port inside stage)
        Scene scene = new Scene(root, 800, 600, Color.BLACK);

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

    private void reallocateIfDiff() {
        FireModel curfireModel = getFireModel();

        // check if a new screen dimension is available
        ScreenDimension newScreenDimension = workerQueue.poll();

        // If not equal reallocate() new one. Also make sure screen dimensions are at least 4x4.
        if (newScreenDimension != null &&
                !newScreenDimension.equals(curfireModel.screenWidth, curfireModel.screenHeight) &&
                newScreenDimension.w > 4 && newScreenDimension.h > 4) {

            curfireModel.cleanup();
            workerQueue.clear();
            FireModel fireModel = new FireModel(newScreenDimension.w, newScreenDimension.h);
            setFireModel(fireModel);
        }
    }

    /**
     * This is called after the Stage has been shown to determine the size of the canvas.
     * @param primaryStage
     */
    private void initCanvas(Stage primaryStage, int heightTop) {
        reallocateIfDiff();

        // Worker thread task to perform convolution (using CPU one thread)
        Task fireTask = new Task() {
            @Override
            protected Void call() throws Exception {

                long startTime = 0;
                long markTime = 0;
                long elapseTime = 0;

                // argb writable surface as opposed to a rgba.
                WritablePixelFormat<IntBuffer> pixelFormat = WritablePixelFormat.getIntArgbInstance();

                while(!this.isCancelled() && !this.isDone()) {
                    // Start stop watch
                    startTime = System.currentTimeMillis();
                    reallocateIfDiff();
                    FireModel fireModel = getFireModel();
                    // Randomize the bottom row of the fire buffer
                    fireModel.genRandomFireRowWidth();
                    fireModel.copyFireRowBottom();

                    markTime = System.currentTimeMillis();

                    fireModel.classic = classic;
                    fireModel.shift1 = shift1;
                    fireModel.shift2 = shift2;
                    fireModel.shift3 = shift3;

                    fireModel.convolution();

                    // let Animation thread draw
                    renderQueue.add(fireModel.copyWritable(pixelFormat));

                    elapseTime = System.currentTimeMillis() - markTime;
                    System.out.println("Convolution takes : " + elapseTime + "ms");
                    elapseTime = System.currentTimeMillis() - startTime;
                    System.out.println("Convolution + setPixels takes : " + elapseTime + "ms");

                    Thread.sleep(DEFAULT_WORKER_SLEEP);
                }
                return null;
            }
        };

        // @TODO possibly make an executor thread pool to distribute work
        // to put more on the render queue to appear faster. Right now the worker takes time to compute image.
        fireTask.setOnFailed(wse -> {
            System.out.println(wse.getEventType().getName());
            fireTask.getException().printStackTrace();
        });

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
                    startTime = System.nanoTime();
                    System.out.println("Num items to render: " + renderQueue.size());

                    // grab work to do
                    WritableImage fireImageFrame = renderQueue.poll();

                    // if there is work to do copy to canvas pixel writer.
                    if (fireImageFrame != null) {
                        canvas.setWidth(fireImageFrame.getWidth());
                        canvas.setHeight(fireImageFrame.getHeight());
                        PixelWriter pw = canvas.getGraphicsContext2D().getPixelWriter();
                        pw.setPixels(0, 0, (int) fireImageFrame.getWidth(), (int) fireImageFrame.getHeight(), fireImageFrame.getPixelReader(), 0, 0);
                    } else {
                        System.out.println("No work to draw");
                        return;
                    }
                    elapseTime = (System.nanoTime() - startTime)/1e6;
                    System.out.println("UI Render thread takes : " + elapseTime + "ms");
                }
            }
        };

        at.start();

        // Debounce the change of width and height of the window resizing.
        DebounceDispatcher resizeListener = new DebounceDispatcher(500)
                .onAction(() -> {
                    // This action is run on the JavaFX ui thread.
                    System.out.println(Thread.currentThread().getName() + " reallocate() called.");

                    int w = (int) Math.ceil(primaryStage.getScene().getWidth());
                    int h = (int) Math.ceil(primaryStage.getScene().getHeight() - heightTop);
                    workerQueue.add(new ScreenDimension(w,h));

                    System.out.println(workerQueue.size());
                    System.out.println(String.format("Reallocate arrays [%s by %s]", w, h));
                });

        // Add debounce dispatcher to listen for width and hight changes.
        primaryStage.getScene().widthProperty().addListener(resizeListener);
        primaryStage.getScene().heightProperty().addListener(resizeListener);
    }
}    