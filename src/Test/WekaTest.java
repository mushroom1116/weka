package Test;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Instances;
/**
 * Created by rong on 2017/3/23.
 */


public class WekaTest {

    public static Instances getFileInstances(String fileName)
            throws FileNotFoundException, IOException {
        Instances m_Instances = new Instances(new BufferedReader(
                new FileReader(fileName)));
        m_Instances.setClassIndex(m_Instances.numAttributes() - 1);
        return m_Instances;
    }

    public static Evaluation crossValidation(Instances m_Instances,
                                             Classifier classifier, int numFolds) throws Exception {
        Evaluation evaluation = new Evaluation(m_Instances);
        evaluation.crossValidateModel(classifier, m_Instances, numFolds,
                new Random(1));
        return evaluation;
    }

    public static void printEvalDetail(Evaluation evaluation) throws Exception {
        System.out.println(evaluation.toClassDetailsString());
        System.out.println(evaluation.toSummaryString());
        System.out.println(evaluation.toMatrixString());
    }

    public static void main(String[] args) throws Exception {

        for(int n=3;n<12;n++) {
            String fileName = "C:\\Users\\rong\\Desktop\\DataSet1\\"+n+"-des.arff";
            Instances data = getFileInstances(fileName);
            //交叉验证
            Evaluation crossEvaluation = crossValidation(data, new J48(), 10);
            TestFile log = new TestFile();
            log.writeLog(n+crossEvaluation.toSummaryString());
            log.writeLog("\r\n");
          /*  printEvalDetail(crossEvaluation);*/
            System.out.println(n+"=====================================");
        }
    }
}
