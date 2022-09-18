package ru.zagarazhi;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

/**
 * Класс оконного приложения
 */
public class App extends Application {

    private static Scene scene; //Базовая сцена приложения
    private StringProperty path = new SimpleStringProperty("Загрузите изображение");
    private int offset = 2; //Количество бит, на которое будет сдвинута каждая пара байт
    private int padding = 0; //Количество строк сверху, которое будет пропущено
    private short[][] pixels; //Исходные данные в формате два байта на пиксель

    /**
     * Метод, превращающий двухбайтную яркость в формат INT_ARGB.
     * Причем Альфа-канал всегда 0xFF.
     * Старшие байты будут срезаны, если выходят за границы
     * @param num Исходная яркость
     * @return Цвет в формате INT_ARGB
     */
    private int cut(short num){
        int temp = (int)((num >>> offset) & 0xFF);
        return (0xFF000000 | (temp << 16) | (temp << 8) | temp);
    }

    /**
     * Метод, для загрузки файла
     */
    public void fileImageBtnPressed() {
        FileChooser fileChooser = new FileChooser();
        FileChooser.ExtensionFilter fileExtension = new FileChooser.ExtensionFilter("Выберете файл", "*.mbv");
        fileChooser.getExtensionFilters().add(fileExtension);
        File mbvFile= fileChooser.showOpenDialog(new Stage());
        if (mbvFile != null) {
            try {
                pixels = MBVFileReader.read(mbvFile);
                path.setValue(mbvFile.getAbsolutePath());
            } catch (SecurityException | IOException e) {
                path.setValue("Не удалось открыть файл");
            }
        } else {
            path.setValue("Файл не найден");
        }
    }

    /**
     * Метод отрисовки изображения с учетом сдвига.
     * При этом размер полотна меняется под размеры изображения с учетом отступа.
     * @param canvas Полотно, в которое будет добавлено изображение
     */
    private void render(Canvas canvas) {
        if(pixels != null) {
            if(pixels.length > 0 && pixels[0].length > 0) {
                canvas.setHeight(pixels.length - padding);
                canvas.setWidth(pixels[0].length);
                PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
                for(int i = padding; i < pixels.length; i++) {
                    for(int j = 0; j < pixels[0].length; j++) {
                        pixelWriter.setArgb(j, i - padding, cut(pixels[i][j]));
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        //Базовая разметка
        BorderPane border = new BorderPane();

        //Основные контейнеры
        HBox controls = new HBox();
        HBox loadControls = new HBox();
        VBox offsetControls = new VBox();
        VBox paddingControls = new VBox();
        VBox info = new VBox();
        VBox canvasBox = new VBox();
        VBox imageInfo = new VBox();

        //Элементы загрузки файла
        Button loadButton = new Button("Загрузить файл");
        Text loadText = new Text();

        //Элементы для работы со сдвигом
        Label sliderLabel = new Label("Выберите сдвиг");
        Label offsetLabel = new Label("Сдвиг: " + offset);
        Slider slider = new Slider(0, 8, 2);
        
        //Элементы для работы с отступом
        Label paddingLabel = new Label("Верхние строки изображения:");
        TextField paddingTextField = new TextField("" + padding);

        //Элементы для работы с позицией курсора относительно полотна
        Label infoText = new Label();
        Label xInfo = new Label();
        Label yInfo = new Label();
        Label lightInfo = new Label();

        //Элементы для указания размеров изображения
        Label imageInfoLabel = new Label();
        Label imageHeight = new Label();
        Label imageWidth = new Label();

        //Полотно и полоса прокрутки для нее
        Canvas canvas = new Canvas(500.0f, 100.0f);
        ScrollPane scrollPane = new ScrollPane(canvas);

        //Обработчик события с изменением значения слайдера.
        //При этом изображение перерисовывается.
        slider.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                int newValue = slider.valueProperty().intValue();
                if(offset != newValue) {
                    offset = newValue;
                    offsetLabel.setText("Сдвиг: " + offset);
                    render(canvas);
                }
            }
        });

        //Обработчик события с изменением значения отсупа.
        //При этом изображение перерисовывается.
        paddingTextField.textProperty().addListener(new ChangeListener<String>() {
            public void changed(ObservableValue <? extends String> observable, String oldValue, String newValue) {
                if(newValue.isEmpty()) newValue = "0";
                int tempPadding = Integer.parseInt(newValue.replaceAll("[^\\d]+", ""));
                if(tempPadding >= 0 && tempPadding < 3000) padding = tempPadding;
                paddingTextField.setText("" + padding);
                if(pixels != null) {
                    if(pixels.length > 0) {
                        imageHeight.setText("Высота: " + (pixels.length - padding));
                    }
                }
                render(canvas);
            }
        });

        //Кнопка загрузки изображения и его первичная отрисовка
        loadButton.setOnAction(
            new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    fileImageBtnPressed();
                    render(canvas);
                    if(pixels != null) {
                        if(pixels.length > 0 && pixels[0].length > 0) {
                            imageInfoLabel.setText("Информация об изображении");
                            imageHeight.setText("Высота: " + (pixels.length - padding));
                            imageWidth.setText("Ширина: " + pixels[0].length);
                        }
                    }
                }
            }
        );

        //Обработчик движения мышки по полотну.
        //Пока курсор находится на изображении происходит считывание координат и отображения цвета по ним.
        //Отступ учитывается.
        canvas.setOnMouseMoved(new EventHandler<MouseEvent>(){
            @Override
            public void handle(MouseEvent event) {
                if(pixels != null) {
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    infoText.setText("Координаты курсора");
                    xInfo.setText("X: " + x);
                    yInfo.setText("Y :" + (y + padding));
                    lightInfo.setText("Яркость: " + pixels[y + padding][x]);
                }
            }
        });

        //Блок размещения элементов на сцене
        canvasBox.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        loadText.textProperty().bind(path);

        canvasBox.setPadding(new Insets(10, 10, 10, 10));
        canvasBox.setAlignment(Pos.BASELINE_CENTER);
        canvasBox.setSpacing(10);

        imageInfo.setPadding(new Insets(10, 10, 10, 10));
        imageInfo.setAlignment(Pos.BASELINE_CENTER);
        imageInfo.setSpacing(10);
        imageInfo.getChildren().addAll(imageInfoLabel, imageHeight, imageWidth, info);

        info.setPadding(new Insets(10, 10, 10, 10));
        info.setAlignment(Pos.BASELINE_CENTER);
        info.setSpacing(10);
        info.getChildren().addAll(infoText, xInfo, yInfo, lightInfo);

        paddingControls.setPadding(new Insets(10, 10, 10, 10));
        paddingControls.setAlignment(Pos.BASELINE_CENTER);
        paddingControls.setSpacing(10);
        paddingControls.getChildren().addAll(paddingLabel, paddingTextField);

        offsetControls.setPadding(new Insets(10, 10, 10, 10));
        offsetControls.setAlignment(Pos.BASELINE_CENTER);
        offsetControls.setSpacing(10);
        offsetControls.getChildren().addAll(sliderLabel, slider, offsetLabel);

        loadControls.setPadding(new Insets(10, 10, 10, 10));
        loadControls.setAlignment(Pos.BASELINE_CENTER);
        loadControls.setSpacing(10);
        loadControls.getChildren().addAll(loadButton, loadText);

        controls.setAlignment(Pos.TOP_CENTER);
        controls.getChildren().addAll(loadControls, offsetControls, paddingControls);

        border.setTop(controls);
        border.setLeft(imageInfo);
        border.setCenter(canvasBox);

        //Настройка и отображение сцены
        scene = new Scene(border, 800, 1000);
        stage.setScene(scene);
        stage.setTitle("Базовый обозреватель");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}