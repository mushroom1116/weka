
/**
 * 功能描述：取一个.arff格式的文件，取其中的200条数据并生成新的.arff文件。
 * 目的及做法：实验需要对比属性个数与分类精确度，因此将属性个数作为变量，
 *              因此需要保证每次实验的数据集大小相同，在这里我取每个数据集的200条数据，
 *               但是下载的数据集中的数据是有序的，因此不能单纯的取前200条，
 *               于是生成一个文件实际数据（不包括前面的声明）行数范围内的200大小的随机整数数组，
 *               再根据整数数组的数据从源文件抽取200条数据行。
 * @author: 王昭蓉
 * @version: V1.0
 * */
package Test;

import java.io.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Random;


/**
 * Created by rong on 2017/3/27.
 */
public class RandomFile {
    /**
     * 得到指定文件的总行数
     *
     * @param path 文件路径及文件名
     * @return int 文件的总行数
     */
    public static int getTotalLineNum(File file) throws IOException {
        int totalLineNum = 0;
        FileReader in = new FileReader(file);
        LineNumberReader reader = new LineNumberReader(in);
        String s = reader.readLine();
        while (s != null) {
            totalLineNum++;
            s = reader.readLine();
        }
        return totalLineNum;
    }

    /**
     * 产生一个数据开始行21行到总行数totalLineNum之间的200大小的随机整数组
     *
     * @param totalLineNum 文件的总行数
     * @return int[] 200大小的随机整数组
     */
    public static int[] randomArray(int totalLineNum) {
        int max = totalLineNum;
        int min = 21;
        int randomArray[] = new int[200];
        Random random = new Random();
        for (int i = 0; i < 200; i++) {
            while (true) {
                int s = random.nextInt(max) % (max - min + 1) + min;
                for (int j = 0; j < i; j++) {
                    if (randomArray[j] == s) {
                        s = -1;
                        break;
                    }
                }
                if (s != -1) {
                    randomArray[i] = s;
                    break;
                }
            }
        }
        return randomArray;
    }

    /**
     * 得到指定文件的特定行
     *
     * @param lineNum 指定行
     * @param path    指定文件
     * @return String 指定行内容
     */
    public static String getSpecificLine(int lineNum, File file) throws IOException {
        FileReader in = new FileReader(file);
        LineNumberReader reader = new LineNumberReader(in);
        String s = null;
        for (int i = 0; i < lineNum; i++) {
            s = reader.readLine();
        }
        reader.close();
        return s;
    }

    /**
     * 给一个200大小的整数数组，读取源文件的数组中数字行写入新文件
     *
     * @param path        源文件路径
     * @param randomArray 数组
     * @paaram pathWrite 新文件路径
     */
    public static void writeNewFile(String pathWrite, File file, int[] randomArray) {
        try {
            File desFile = new File(pathWrite);
            if (!desFile.exists())
                desFile.createNewFile();
            FileOutputStream out = new FileOutputStream(desFile, true); //如果追加方式用true
            StringBuffer sb = new StringBuffer();
            FileReader in = new FileReader(file);
            LineNumberReader reader = new LineNumberReader(in);
            String s = null;
            for (int i = 0; i < 20; i++) {
                s = reader.readLine();
                sb.append(s + "\n");
            }
            reader.close();

            for (int i = 0; i < randomArray.length; i++) {
                s = getSpecificLine(randomArray[i], file);
                sb.append(s + "\n");
            }
            out.write(sb.toString().getBytes("utf-8"));//注意需要转换对应的字符集
            reader.close();
            out.close();
        } catch (IOException ex) {
            System.out.println(ex.getStackTrace());
        }

    }

    public static String replaceSpace(String s)
    {
        char[] charArray = s.toCharArray();
        StringBuilder sBuilder = new StringBuilder();
        for(char c : charArray)
        {
            if(c == ' ')
            {

                sBuilder.append(",");
            }else {
                sBuilder.append(c);
            }
        }
        String string = sBuilder.toString();
        return string;
    }
    /**
     * 将第一列换到最后一列
     * @param s 待转换的字符串
     * @return String 转换后的字符串*/
    public static String replacePosition(String s)
    {
        char[] charArray = s.toCharArray();
        StringBuilder sBuilder = new StringBuilder();
        char temp = charArray[0];
        for(int i = 2;i<charArray.length;i++)
        {
            sBuilder.append(charArray[i]);
        }
        sBuilder.append(","+temp);
        String string = sBuilder.toString();
        return string;
    }
/**
 * 将数据集中的空格替换成','
 * @param path 新文件路径
 * @param file 需要替换的文件
 * @param String 返回替换后的文件
 * */
    public static void replaceSpace(String pathWrite,File file)
    {
        try
        {
            File  desFile= new File(pathWrite);
            if(!desFile.exists())
                desFile.createNewFile();
            FileOutputStream out=new FileOutputStream(desFile,true); //如果追加方式用true
            StringBuffer sb=new StringBuffer();
            FileReader in = new FileReader(file);
            LineNumberReader reader = new LineNumberReader(in);
            String s = null;
            int num = getTotalLineNum(file);
            for(int i = 0;i<num;i++) {
                s = reader.readLine();
                s = replaceSpace(s);
                sb.append(s+"\n");
            }
            reader.close();
            out.write(sb.toString().getBytes("utf-8"));//注意需要转换对应的字符集
            out.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getStackTrace());
        }
    }

    /**
     * 将数据集第一列转换到最后一列
     * @param path 新文件路径
     * @param file 需要替换的文件
     * @param String 返回替换后的文件
     * */
    public static void replacePositio(String pathWrite,File file)
    {
        try
        {
            File  desFile= new File(pathWrite);
            if(!desFile.exists())
                desFile.createNewFile();
            FileOutputStream out=new FileOutputStream(desFile,true); //如果追加方式用true
            StringBuffer sb=new StringBuffer();
            FileReader in = new FileReader(file);
            LineNumberReader reader = new LineNumberReader(in);
            String s = null;
            int num = getTotalLineNum(file);
            for(int i = 0;i<num;i++) {
                s = reader.readLine();
                s = replacePosition(s);
                sb.append(s+"\n");
            }
            reader.close();
            out.write(sb.toString().getBytes("utf-8"));//注意需要转换对应的字符集
            out.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getStackTrace());
        }
    }

    public static void main(String[] args) throws IOException
    {
        String path = "C:\\Users\\rong\\Desktop\\DataSet1\\10.arff";
        File file = new File(path);
        RandomFile randomFile = new RandomFile();
        int totalLineNum = randomFile.getTotalLineNum(file);

        /**测试getTotalLineNum*/
        System.out.println("totalLineNum:"+totalLineNum);
        int[] randomArray = randomFile.randomArray(totalLineNum);

        /**
         * 测试randomArray*/
        System.out.println(randomArray.length);
        System.out.print("randomArray:[");
        for(int i=0;i<200;i++)
        {
            if(i != 199)
                System.out.print(randomArray[i]+",");
            else System.out.println(randomArray[i]+"]");
        }

        /**
         * 测试getSpecificLine*/
        String s = randomFile.getSpecificLine(46,file);
        System.out.println("s:"+s);

        /**
         * 测试writeNewFile*/
        String desPath = "C:\\Users\\rong\\Desktop\\DataSet1\\10-des.arff";
        randomFile.writeNewFile(desPath,file,randomArray);

//        /**
//         * 测试replaceSpace*/
//        String s = "C:\\Users\\rong\\Desktop\\DataSet\\training.arff";
//        File f = new File(s);
//        String newS = "C:\\Users\\rong\\Desktop\\DataSet\\training-1.arff";
//        replacePositio(newS,f);
    }

}



