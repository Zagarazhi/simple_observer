package ru.zagarazhi;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Класс чтения файлов формата .mbv
 */
public class MBVFileReader {
    
    /**
     * Метод для чтения файлов формата .mbv
     * @param file Файл необходимого формата.
     * @return Двумерный массив типа short[][], в котором хранятся значение яркости каждого пикселя.
     * При этом значение может превышать 255.
     * @throws IOException Ошибка, вызванная отсутсвием файла или нарушением его целостности.
     * @throws SecurityException Ошибка доступа к файлу.
     */
    public static short[][] read(File file) throws IOException, SecurityException{
        short width = 0;
        short height = 0;
        short[][] result = null;

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {

            //Запись всего файла в одномерный байтовый массив
            byte[] temp = dis.readAllBytes();
            if(temp.length < 4) throw new IOException(".mbv file is empty");

            /*
             * Этап сборки двухбайтовых значений из массива байт.
             * Конструкция temp[n] & 0xFF позволяет не учитывать знаковый байт и работать с типом byte как с 8 битами
             * number << 8 - побитовый сдвиг влево на 8 бит. Этот байт становится старшим.
             * number | number - операция ИЛИ, которая позволяет "соединить" два байта
             * (short) - привидение к типу short
             */
            width = (short)(((temp[1] & 0xFF) << 8) | (temp[0] & 0xFF));
            height = (short)(((temp[3] & 0xFF) << 8) | (temp[2] & 0xFF));
            if(width < 0 | height < 0) throw new IOException("Invalid image bounderies");

            //Преобразование одномерного массива типа byte в двумерный массив типа short.
            //При этом отслеживается выход за границы массива
            int position = 4;
            int arrLength = temp.length;
            result = new short[height][width];
            for(int i = 0; i < height; i++) {
                for(int j = 0; j < width; j++) {
                    if(position + 1 >= arrLength) throw new IOException("Empty bytes");
                    //По условию задания все биты кроме десяти значимых должны быть нулями,
                    //поэтому младший байт записывается полностью, а в старшем только два младших бита
                    result[i][j] = (short)(((temp[position + 1] & 0x3) << 8) | (temp[position] & 0xFF));
                    position += 2;
                }
            }
        } catch (IOException fileNotFoundException) {
            throw new IOException(".mbv file not found");
        } catch (SecurityException securityException) {
            throw securityException;
        }
        return result;
    }
}
