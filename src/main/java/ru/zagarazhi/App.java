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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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
    private int scale = 2; //Порядок увеличения изображения
    private int areaSize = 50; //Размер исходного участка
    private int padding = 0; //Количество строк сверху, которое будет пропущено
    private short[][] pixels; //Исходные данные в формате два байта на пиксель
    private boolean cleared = true; //Флаг очистки увеличенной области 
    private boolean stopped = false; //Флаг остановки перемещения
    private int stoppedX, stoppedY; //Координаты зафиксированного пикселя

    /**
     * Метод, превращающий двухбайтную яркость в формат INT_ARGB.
     * Причем Альфа-канал всегда 0xFF.
     * Старшие байты будут срезаны, если выходят за границы
     * @param num Исходная яркость
     * @return Цвет в формате INT_ARGB
     */
    private int cut(short num) {
        int temp = (int)((num >>> offset) & 0xFF);
        return (0xFF000000 | (temp << 16) | (temp << 8) | temp);
    }

    /**
     * Метод очистки поля отрисовки изображения
     * @param canvas Очищаемое поле
     */
    private void clear(Canvas canvas) {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
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
                path.setValue(mbvFile.getName());
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

    /**
     * Метод отрисовки уменьшенного изображения
     * Размер получаемого изображения зависит от размера полотна, в которое оно будет записано
     * @param canvas Полотно, в которое будет добавлено изображение
     */
    private void miniRender(Canvas canvas) {
        if(pixels != null) {
            if(pixels.length > 0 && pixels[0].length > 0) {
                int deltaX = (int)(pixels[0].length / canvas.getWidth());
                int deltaY = (int)(pixels.length / canvas.getHeight());
                PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
                for(int i = padding; i < pixels.length; i += deltaY) {
                    for(int j = 0; j < pixels[0].length; j += deltaX) {
                        pixelWriter.setArgb(j / deltaX, (i - padding) / deltaY, cut(pixels[i][j]));
                    }
                }
            }
        }
    }

    /**
     * Отрисовка увеличенного изображения методом ближайшего соседа без нормализации
     * @param x Координата x середины увеличиваемой области
     * @param y Координата y середины увеличиваемой области
     * @param canvas Полотно, на котором будет отрисовано изображение
     */
    private void areaRenderNeighbor(int x, int y, Canvas canvas) {
        int cornerX = x - areaSize / 2; //Координата X левого верхнего угла
        int cornerY = y - areaSize / 2; //Координата Y левого верхнего угла 
        int color;
        PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
        for(int i = 0; i < areaSize; i++) {
            for(int j = 0; j < areaSize; j++) {
                color = cut(pixels[cornerY + i + padding][cornerX + j]);
                for(int m = 0; m < scale; m++) {
                    for(int n = 0; n < scale; n++) {
                        pixelWriter.setArgb(j * scale + m, i * scale + n, color);
                    }
                }
            }
        }
        cleared = false;
    }

    /**
     * Отрисовка увеличенного изображения методом ближайшего соседа c нормализацией
     * @param x Координата x середины увеличиваемой области
     * @param y Координата y середины увеличиваемой области
     * @param canvas Полотно, на котором будет отрисовано изображение
     */
    private void areaRenderNeighborNormalaze(int x, int y, Canvas canvas) {
        int cornerX = x - areaSize / 2; //Координата X левого верхнего угла
        int cornerY = y - areaSize / 2; //Координата Y левого верхнего угла 
        int temp;
        int light, minLight = 255, maxLight = 0;
        double coef = 0;
        PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
        //Определения самого яркого и самого тусклого пикселей и коффицента,
        //который преобразует эту область в числа в диапазоне [0;255]
        for(int i = cornerY + padding; i < cornerY + padding + areaSize; i++) {
            for(int j = cornerX; j < cornerX + areaSize; j++) {
                temp = pixels[i][j];
                if(temp < minLight) minLight = temp;
                if(temp > maxLight) maxLight = temp;
            }
        }
        coef = 255.0f / (maxLight - minLight);
        for(int i = 0; i < areaSize; i++) {
            for(int j = 0; j < areaSize; j++) {
                light = (int) ((pixels[cornerY + i + padding][cornerX + j] - minLight) * coef);
                light = (0xFF000000 | (light << 16) | (light << 8) | light);
                for(int m = 0; m < scale; m++) {
                    for(int n = 0; n < scale; n++) {
                        pixelWriter.setArgb(j * scale + m, i * scale + n, light);
                    }
                }
            }
        }
        cleared = false;
    }

    /**
     * Отрисовка увеличенного изображения методом билинейной интерполяции без нормализации
     * @param x Координата x середины увеличиваемой области
     * @param y Координата y середины увеличиваемой области
     * @param canvas Полотно, на котором будет отрисовано изображение
     */
    private void areaRenderInterpolation(int x, int y, Canvas canvas) {
        int cornerX = x - areaSize / 2; //Координата X левого верхнего угла
        int cornerY = y - areaSize / 2; //Координата Y левого верхнего угла 
        int a, b, c, d;
        int color;
        PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
        for(int i = 0; i < areaSize; i++) {
            for(int j = 0; j < areaSize; j++) {
                d = pixels[cornerY + i + padding][cornerX + j];
                a = pixels[cornerY + i + padding][cornerX + j + 1] - d;
                b = pixels[cornerY + i + padding + 1][cornerX + j] - d;
                c = pixels[cornerY + i + padding + 1][cornerX + j + 1] - a - b - d;
                for(int m = 0; m < scale; m++) {
                    for(int n = 0; n < scale; n++) {
                        color = cut((short)((
                            (a * (double)n / scale)
                            + (b * (double)m / scale)
                            + (c * (double)n / scale * (double)m / scale)
                            + d)));
                        pixelWriter.setArgb(j * scale + n, i * scale + m, color);
                    }
                }
            }
        }
        cleared = false;
    }

    /**
     * Отрисовка увеличенного изображения методом билинейной интерполяции с нормализацией
     * @param x Координата x середины увеличиваемой области
     * @param y Координата y середины увеличиваемой области
     * @param canvas Полотно, на котором будет отрисовано изображение
     */
    private void areaRenderInterpolationNormalize(int x, int y, Canvas canvas) {
        int cornerX = x - areaSize / 2; //Координата X левого верхнего угла
        int cornerY = y - areaSize / 2; //Координата Y левого верхнего угла 
        int a, b, c, d;
        int light;
        int temp, minLight = 255, maxLight = 0;
        double coef = 0;
        PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();
        //Определения самого яркого и самого тусклого пикселей и коффицента,
        //который преобразует эту область в числа в диапазоне [0;255]
        for(int i = cornerY + padding; i < cornerY + padding + areaSize; i++) {
            for(int j = cornerX; j < cornerX + areaSize; j++) {
                temp = pixels[i][j];
                if(temp < minLight) minLight = temp;
                if(temp > maxLight) maxLight = temp;
            }
        }
        coef = 255.0f / (maxLight - minLight);
        for(int i = 0; i < areaSize; i++) {
            for(int j = 0; j < areaSize; j++) {
                d = pixels[cornerY + i + padding][cornerX + j];
                a = pixels[cornerY + i + padding][cornerX + j + 1] - d;
                b = pixels[cornerY + i + padding + 1][cornerX + j] - d;
                c = pixels[cornerY + i + padding + 1][cornerX + j + 1] - a - b - d;
                for(int m = 0; m < scale; m++) {
                    for(int n = 0; n < scale; n++) {
                        light = (int)(((
                            (a * (double)n / scale)
                            + (b * (double)m / scale)
                            + (c * (double)n / scale * (double)m / scale)
                            + d) - minLight) * coef);
                        //
                        if(light < 0) light = 0;
                        if(light > 255) light = 255;
                        light = (0xFF000000 | (light << 16) | (light << 8) | light);
                        pixelWriter.setArgb(j * scale + n, i * scale + m, light);
                    }
                }
            }
        }
        cleared = false;
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
        VBox multiplierSliderBox = new VBox();
        VBox multiplierControls = new VBox();
        VBox multiplierCanvasBox = new VBox();
        VBox miniCanvasBox = new VBox();
        VBox areaSizeControls = new VBox();
        HBox main = new HBox();

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
        Canvas canvas = new Canvas(500.0f, 500.0f);
        ScrollPane scrollPane = new ScrollPane(canvas);

        //Полотно для обзорного изображения
        Canvas miniCanvas = new Canvas(100.0f, 600.0f);

        //Полотно для увеличенного изображения
        Canvas multiplierCanvas = new Canvas(500.0f, 500.0f);

        //Элементы для настройки увеличения
        Label multiplierInfo = new Label("Степень увеличения: ");
        Label multiplierInfoLabel = new Label("Увеличение: 2");
        Label areaSizeInfoLabel = new Label("Размер поля: ");
        Label areaSizeLabel = new Label("Размер области: 50");
        Slider multiplierSlider = new Slider(2, 5, 2);
        Slider areaSizeSlider = new Slider(10, 100, 50);
        ToggleGroup multiplierGroup = new ToggleGroup();
        RadioButton neighbor = new RadioButton("Метод ближайшего соседа");
        RadioButton interpolation = new RadioButton("Метод билинейной интерполяции");
        CheckBox normalaze = new CheckBox("Нормировать яркость");

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
                    clear(miniCanvas);
                    miniRender(miniCanvas);
                }
            }
        });

        //Обработчик события изменения слайдера
        //При это меняется увеличение изображения
        multiplierSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> number, Number oldValue, Number newValue) {
                if(scale != newValue.intValue()) {
                    multiplierInfoLabel.setText("Увеличение: " + newValue.intValue());
                    scale = newValue.intValue();
                    if(!cleared) {
                        clear(multiplierCanvas);
                        cleared = true;
                    }
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
                clear(miniCanvas);
                miniRender(miniCanvas);
            }
        });

        //Кнопка загрузки изображения и его первичная отрисовка
        loadButton.setOnAction(
            new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    fileImageBtnPressed();
                    render(canvas);
                    miniRender(miniCanvas);
                    if(pixels != null) {
                        if(pixels.length > 0 && pixels[0].length > 0) {
                            imageInfoLabel.setText("Информация об изображении: ");
                            infoText.setText("Координаты курсора: ");
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
                    int x = stopped ? stoppedX : (int)event.getX();
                    int y = stopped ? stoppedY : (int)event.getY();
                    xInfo.setText("X: " + x);
                    yInfo.setText("Y :" + (y + padding));
                    lightInfo.setText("Яркость: " + pixels[y + padding][x]);
                    if(x > areaSize / 2 && y > areaSize / 2 && x < (canvas.getWidth() - areaSize / 2) && y < (canvas.getHeight() - areaSize /2)) {
                        if(neighbor.isSelected()) {
                            if(normalaze.isSelected()) {
                                areaRenderNeighborNormalaze(x, y, multiplierCanvas);
                            } else {
                                areaRenderNeighbor(x, y, multiplierCanvas);
                            }
                        } else if(interpolation.isSelected()) {
                            if(normalaze.isSelected()) {
                                areaRenderInterpolationNormalize(x, y, multiplierCanvas);
                            } else {
                                areaRenderInterpolation(x, y, multiplierCanvas);
                            }
                        }
                    }
                }
            }
        });

        //Обработчик нажатий по обзорному изображению
        miniCanvas.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if(pixels != null) {
                    scrollPane.setVvalue(event.getY() / miniCanvas.getHeight());
                }
            }
        });

        //Задержка выбранной области
        canvas.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                stopped = !stopped;
                stoppedX = (int)event.getX();
                stoppedY = (int)event.getY();
            }
        });

        //Обработчик события изменения слайдера
        //При это меняется размер увеличиваемой области
        areaSizeSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> number, Number oldValue, Number newValue) {
                if(areaSize != newValue.intValue()) {
                    areaSizeLabel.setText("Размер области: " + newValue.intValue());
                    areaSize = newValue.intValue();
                    if(!cleared) {
                        clear(multiplierCanvas);
                        cleared = true;
                    }
                }
            }
        });

        //Блок размещения элементов на сцене
        imageInfoLabel.setMinWidth(180);

        neighbor.setToggleGroup(multiplierGroup);
        neighbor.setSelected(true);
        interpolation.setToggleGroup(multiplierGroup);

        canvasBox.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        loadText.textProperty().bind(path);

        canvasBox.setPadding(new Insets(10, 10, 10, 10));
        canvasBox.setAlignment(Pos.BASELINE_CENTER);
        canvasBox.setSpacing(10);
        canvasBox.setMinWidth(550);

        multiplierSliderBox.setPadding(new Insets(10, 10, 10, 10));
        multiplierSliderBox.setAlignment(Pos.BASELINE_CENTER);
        multiplierSliderBox.setSpacing(10);
        multiplierSliderBox.getChildren().addAll(multiplierInfo, multiplierSlider, multiplierInfoLabel);

        areaSizeControls.setPadding(new Insets(10, 10, 10, 10));
        areaSizeControls.setAlignment(Pos.BASELINE_CENTER);
        areaSizeControls.setSpacing(10);
        areaSizeControls.getChildren().addAll(areaSizeInfoLabel, areaSizeSlider, areaSizeLabel);

        multiplierControls.setPadding(new Insets(10, 10, 10, 10));
        multiplierControls.setAlignment(Pos.BASELINE_CENTER);
        multiplierControls.setSpacing(10);
        multiplierControls.getChildren().addAll(neighbor, interpolation, normalaze);

        multiplierCanvasBox.setPadding(new Insets(10, 10, 10, 10));
        multiplierCanvasBox.setAlignment(Pos.BASELINE_CENTER);
        multiplierCanvasBox.setSpacing(10);
        multiplierCanvasBox.getChildren().addAll(multiplierCanvas);

        miniCanvasBox.setPadding(new Insets(10, 10, 10, 10));
        miniCanvasBox.setAlignment(Pos.BASELINE_CENTER);
        miniCanvasBox.setSpacing(10);
        miniCanvasBox.getChildren().addAll(miniCanvas);

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
        controls.getChildren().addAll(loadControls, offsetControls, paddingControls, areaSizeControls, multiplierSliderBox, multiplierControls);

        main.getChildren().addAll(miniCanvasBox, canvasBox);

        border.setTop(controls);
        border.setLeft(imageInfo);
        border.setCenter(main);
        border.setRight(multiplierCanvasBox);

        //Настройка и отображение сцены
        scene = new Scene(border, 1500, 1000);
        stage.setScene(scene);
        stage.setTitle("Базовый обозреватель");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}