//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package weka.classifiers;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Sourcable;
import weka.classifiers.UpdateableClassifier;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.ThresholdCurve;
import weka.classifiers.pmml.consumer.PMMLClassifier;
import weka.classifiers.xml.XMLClassifier;
import weka.core.Drawable;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Range;
import weka.core.RevisionHandler;
import weka.core.RevisionUtils;
import weka.core.Summarizable;
import weka.core.Utils;
import weka.core.Version;
import weka.core.converters.ConverterUtils.DataSink;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.pmml.PMMLFactory;
import weka.core.pmml.PMMLModel;
import weka.core.xml.KOML;
import weka.core.xml.XMLOptions;
import weka.estimators.Estimator;
import weka.estimators.KernelEstimator;

public class Evaluation implements Summarizable, RevisionHandler {
    protected int m_NumClasses;
    protected int m_NumFolds;
    protected double m_Incorrect;
    protected double m_Correct;
    protected double m_Unclassified;
    protected double m_MissingClass;
    protected double m_WithClass;
    protected double[][] m_ConfusionMatrix;
    protected String[] m_ClassNames;
    protected boolean m_ClassIsNominal;
    protected double[] m_ClassPriors;
    protected double m_ClassPriorsSum;
    protected CostMatrix m_CostMatrix;
    protected double m_TotalCost;
    protected double m_SumErr;
    protected double m_SumAbsErr;
    protected double m_SumSqrErr;
    protected double m_SumClass;
    protected double m_SumSqrClass;
    protected double m_SumPredicted;
    protected double m_SumSqrPredicted;
    protected double m_SumClassPredicted;
    protected double m_SumPriorAbsErr;
    protected double m_SumPriorSqrErr;
    protected double m_SumKBInfo;
    protected static int k_MarginResolution = 500;
    protected double[] m_MarginCounts;
    protected int m_NumTrainClassVals;
    protected double[] m_TrainClassVals;
    protected double[] m_TrainClassWeights;
    protected Estimator m_PriorErrorEstimator;
    protected Estimator m_ErrorEstimator;
    protected static final double MIN_SF_PROB = 4.9E-324D;
    protected double m_SumPriorEntropy;
    protected double m_SumSchemeEntropy;
    private FastVector m_Predictions;
    protected boolean m_NoPriors;

    public Evaluation(Instances data) throws Exception {
        this(data, (CostMatrix)null);
    }

    public Evaluation(Instances data, CostMatrix costMatrix) throws Exception {
        this.m_NoPriors = false;
        this.m_NumClasses = data.numClasses();
        this.m_NumFolds = 1;
        this.m_ClassIsNominal = data.classAttribute().isNominal();
        if(this.m_ClassIsNominal) {
            this.m_ConfusionMatrix = new double[this.m_NumClasses][this.m_NumClasses];
            this.m_ClassNames = new String[this.m_NumClasses];

            for(int i = 0; i < this.m_NumClasses; ++i) {
                this.m_ClassNames[i] = data.classAttribute().value(i);
            }
        }

        this.m_CostMatrix = costMatrix;
        if(this.m_CostMatrix != null) {
            if(!this.m_ClassIsNominal) {
                throw new Exception("Class has to be nominal if cost matrix given!");
            }

            if(this.m_CostMatrix.size() != this.m_NumClasses) {
                throw new Exception("Cost matrix not compatible with data!");
            }
        }

        this.m_ClassPriors = new double[this.m_NumClasses];
        this.setPriors(data);
        this.m_MarginCounts = new double[k_MarginResolution + 1];
    }

    public double areaUnderROC(int classIndex) {
        if(this.m_Predictions == null) {
            return Instance.missingValue();
        } else {
            ThresholdCurve tc = new ThresholdCurve();
            Instances result = tc.getCurve(this.m_Predictions, classIndex);
            return ThresholdCurve.getROCArea(result);
        }
    }

    public double weightedAreaUnderROC() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int aucTotal = 0; aucTotal < this.m_NumClasses; ++aucTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[aucTotal] += this.m_ConfusionMatrix[aucTotal][j];
            }

            classCountSum += classCounts[aucTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.areaUnderROC(i);
            if(!Instance.isMissingValue(temp)) {
                var9 += temp * classCounts[i];
            }
        }

        return var9 / classCountSum;
    }

    public double[][] confusionMatrix() {
        double[][] newMatrix = new double[this.m_ConfusionMatrix.length][0];

        for(int i = 0; i < this.m_ConfusionMatrix.length; ++i) {
            newMatrix[i] = new double[this.m_ConfusionMatrix[i].length];
            System.arraycopy(this.m_ConfusionMatrix[i], 0, newMatrix[i], 0, this.m_ConfusionMatrix[i].length);
        }

        return newMatrix;
    }

    public void crossValidateModel(Classifier classifier, Instances data, int numFolds, Random random, Object... forPredictionsPrinting) throws Exception {
        data = new Instances(data);
        data.randomize(random);
        if(data.classAttribute().isNominal()) {
            data.stratify(numFolds);
        }

        if(forPredictionsPrinting.length > 0) {
            StringBuffer i = (StringBuffer)forPredictionsPrinting[0];
            Range train = (Range)forPredictionsPrinting[1];
            boolean copiedClassifier = ((Boolean)forPredictionsPrinting[2]).booleanValue();
            printClassificationsHeader(data, train, copiedClassifier, i);
        }

        for(int var10 = 0; var10 < numFolds; ++var10) {
            Instances var11 = data.trainCV(numFolds, var10, random);
            this.setPriors(var11);
            Classifier var12 = Classifier.makeCopy(classifier);
            var12.buildClassifier(var11);
            Instances test = data.testCV(numFolds, var10);
            this.evaluateModel(var12, test, forPredictionsPrinting);
        }

        this.m_NumFolds = numFolds;
    }

    public void crossValidateModel(String classifierString, Instances data, int numFolds, String[] options, Random random) throws Exception {
        this.crossValidateModel(Classifier.forName(classifierString, options), data, numFolds, random, new Object[0]);
    }

    public static String evaluateModel(String classifierString, String[] options) throws Exception {
        Classifier classifier;
        try {
            classifier = (Classifier)Class.forName(classifierString).newInstance();
        } catch (Exception var4) {
            throw new Exception("Can\'t find class with name " + classifierString + '.');
        }

        return evaluateModel(classifier, options);
    }

    public static void main(String[] args) {
        try {
            if(args.length == 0) {
                throw new Exception("The first argument must be the class name of a classifier");
            }

            String ex = args[0];
            args[0] = "";
            System.out.println(evaluateModel(ex, args));
        } catch (Exception var2) {
            var2.printStackTrace();
            System.err.println(var2.getMessage());
        }

    }

    public static String evaluateModel(Classifier classifier, String[] options) throws Exception {
        Instances train = null;
        Instances test = null;
        Instances template = null;
        int seed = 1;
        int folds = 10;
        int classIndex = -1;
        boolean noCrossValidation = false;
        boolean noOutput = false;
        boolean printClassifications = false;
        boolean trainStatistics = true;
        boolean printMargins = false;
        boolean printComplexityStatistics = false;
        boolean printGraph = false;
        boolean classStatistics = false;
        boolean printSource = false;
        StringBuffer text = new StringBuffer();
        DataSource trainSource = null;
        DataSource testSource = null;
        ObjectInputStream objectInputStream = null;
        BufferedInputStream xmlInputStream = null;
        CostMatrix costMatrix = null;
        StringBuffer schemeOptionsText = null;
        Range attributesToOutput = null;
        long trainTimeStart = 0L;
        long trainTimeElapsed = 0L;
        long testTimeStart = 0L;
        long testTimeElapsed = 0L;
        String xml = "";
        String[] optionsTmp = null;
        boolean printDistribution = false;
        int actualClassIndex = -1;
        String splitPercentageString = "";
        double splitPercentage = -1.0D;
        boolean preserveOrder = false;
        boolean trainSetPresent = false;
        boolean testSetPresent = false;
        StringBuffer predsBuff = null;
        if(!Utils.getFlag("h", options) && !Utils.getFlag("help", options)) {
            String sourceClass;
            String objectInputFileName;
            String objectOutputFileName;
            String thresholdFile;
            String thresholdLabel;
            Instances result;
            int var74;
            try {
                xml = Utils.getOption("xml", options);
                if(!xml.equals("")) {
                    options = (new XMLOptions(xml)).toArray();
                }

                optionsTmp = new String[options.length];

                for(int var69 = 0; var69 < options.length; ++var69) {
                    optionsTmp[var69] = options[var69];
                }

                String var70 = Utils.getOption('l', optionsTmp);
                if(var70.endsWith(".xml")) {
                    boolean testingEvaluation = false;

                    try {
                        PMMLModel labelIndex = PMMLFactory.getPMMLModel(var70);
                        if(labelIndex instanceof PMMLClassifier) {
                            classifier = (PMMLClassifier)labelIndex;
                            testingEvaluation = true;
                        }
                    } catch (IllegalArgumentException var65) {
                        testingEvaluation = false;
                    }

                    if(!testingEvaluation) {
                        XMLClassifier var72 = new XMLClassifier();
                        Classifier tc = (Classifier)var72.read(Utils.getOption('l', options));
                        optionsTmp = new String[options.length + tc.getOptions().length];
                        System.arraycopy(tc.getOptions(), 0, optionsTmp, 0, tc.getOptions().length);
                        System.arraycopy(options, 0, optionsTmp, tc.getOptions().length, options.length);
                        options = optionsTmp;
                    }
                }

                noCrossValidation = Utils.getFlag("no-cv", options);
                String classIndexString = Utils.getOption('c', options);
                if(classIndexString.length() != 0) {
                    if(classIndexString.equals("first")) {
                        classIndex = 1;
                    } else if(classIndexString.equals("last")) {
                        classIndex = -1;
                    } else {
                        classIndex = Integer.parseInt(classIndexString);
                    }
                }

                String trainFileName = Utils.getOption('t', options);
                objectInputFileName = Utils.getOption('l', options);
                objectOutputFileName = Utils.getOption('d', options);
                String testFileName = Utils.getOption('T', options);
                String foldsString = Utils.getOption('x', options);
                if(foldsString.length() != 0) {
                    folds = Integer.parseInt(foldsString);
                }

                String seedString = Utils.getOption('s', options);
                if(seedString.length() != 0) {
                    seed = Integer.parseInt(seedString);
                }

                if(trainFileName.length() == 0) {
                    if(objectInputFileName.length() == 0) {
                        throw new Exception("No training file and no object input file given.");
                    }

                    if(testFileName.length() == 0) {
                        throw new Exception("No training file and no test file given.");
                    }
                } else if(objectInputFileName.length() != 0 && (!(classifier instanceof UpdateableClassifier) || testFileName.length() == 0)) {
                    throw new Exception("Classifier not incremental, or no test file provided: can\'t use both train and model file.");
                }

                try {
                    if(trainFileName.length() != 0) {
                        trainSetPresent = true;
                        trainSource = new DataSource(trainFileName);
                    }

                    if(testFileName.length() != 0) {
                        testSetPresent = true;
                        testSource = new DataSource(testFileName);
                    }

                    if(objectInputFileName.length() != 0) {
                        if(objectInputFileName.endsWith(".xml")) {
                            objectInputStream = null;
                            xmlInputStream = null;
                        } else {
                            Object var73 = new FileInputStream(objectInputFileName);
                            if(objectInputFileName.endsWith(".gz")) {
                                var73 = new GZIPInputStream((InputStream)var73);
                            }

                            if(objectInputFileName.endsWith(".koml") && KOML.isPresent()) {
                                objectInputStream = null;
                                xmlInputStream = new BufferedInputStream((InputStream)var73);
                            } else {
                                objectInputStream = new ObjectInputStream((InputStream)var73);
                                xmlInputStream = null;
                            }
                        }
                    }
                } catch (Exception var66) {
                    throw new Exception("Can\'t open file " + var66.getMessage() + '.');
                }

                int var75;
                if(testSetPresent) {
                    template = test = testSource.getStructure();
                    if(classIndex != -1) {
                        test.setClassIndex(classIndex - 1);
                    } else if(test.classIndex() == -1 || classIndexString.length() != 0) {
                        test.setClassIndex(test.numAttributes() - 1);
                    }

                    actualClassIndex = test.classIndex();
                } else {
                    splitPercentageString = Utils.getOption("split-percentage", options);
                    if(splitPercentageString.length() != 0) {
                        if(foldsString.length() != 0) {
                            throw new Exception("Percentage split cannot be used in conjunction with cross-validation (\'-x\').");
                        }

                        splitPercentage = Double.parseDouble(splitPercentageString);
                        if(splitPercentage <= 0.0D || splitPercentage >= 100.0D) {
                            throw new Exception("Percentage split value needs be >0 and <100.");
                        }
                    } else {
                        splitPercentage = -1.0D;
                    }

                    preserveOrder = Utils.getFlag("preserve-order", options);
                    if(preserveOrder && splitPercentage == -1.0D) {
                        throw new Exception("Percentage split (\'-split-percentage\') is missing.");
                    }

                    if(splitPercentage > 0.0D) {
                        testSetPresent = true;
                        Instances var77 = trainSource.getDataSet(actualClassIndex);
                        if(!preserveOrder) {
                            var77.randomize(new Random((long)seed));
                        }

                        var74 = (int)Math.round((double)var77.numInstances() * splitPercentage / 100.0D);
                        var75 = var77.numInstances() - var74;
                        result = new Instances(var77, 0, var74);
                        Instances testInst = new Instances(var77, var74, var75);
                        trainSource = new DataSource(result);
                        testSource = new DataSource(testInst);
                        template = test = testSource.getStructure();
                        if(classIndex != -1) {
                            test.setClassIndex(classIndex - 1);
                        } else if(test.classIndex() == -1 || classIndexString.length() != 0) {
                            test.setClassIndex(test.numAttributes() - 1);
                        }

                        actualClassIndex = test.classIndex();
                    }
                }

                if(trainSetPresent) {
                    template = train = trainSource.getStructure();
                    if(classIndex != -1) {
                        train.setClassIndex(classIndex - 1);
                    } else if(train.classIndex() == -1 || classIndexString.length() != 0) {
                        train.setClassIndex(train.numAttributes() - 1);
                    }

                    actualClassIndex = train.classIndex();
                    if(testSetPresent && !test.equalHeaders(train)) {
                        throw new IllegalArgumentException("Train and test file not compatible!");
                    }
                }

                if(template == null) {
                    throw new Exception("No actual dataset provided to use as template");
                }

                costMatrix = handleCostOption(Utils.getOption('m', options), template.numClasses());
                classStatistics = Utils.getFlag('i', options);
                noOutput = Utils.getFlag('o', options);
                trainStatistics = !Utils.getFlag('v', options);
                printComplexityStatistics = Utils.getFlag('k', options);
                printMargins = Utils.getFlag('r', options);
                printGraph = Utils.getFlag('g', options);
                sourceClass = Utils.getOption('z', options);
                printSource = sourceClass.length() != 0;
                printDistribution = Utils.getFlag("distribution", options);
                thresholdFile = Utils.getOption("threshold-file", options);
                thresholdLabel = Utils.getOption("threshold-label", options);

                String attributeRangeString;
                try {
                    attributeRangeString = Utils.getOption('p', options);
                } catch (Exception var64) {
                    throw new Exception(var64.getMessage() + "\nNOTE: the -p option has changed. " + "It now expects a parameter specifying a range of attributes " + "to list with the predictions. Use \'-p 0\' for none.");
                }

                if(attributeRangeString.length() != 0) {
                    printClassifications = true;
                    noOutput = true;
                    if(!attributeRangeString.equals("0")) {
                        attributesToOutput = new Range(attributeRangeString);
                    }
                }

                if(!printClassifications && printDistribution) {
                    throw new Exception("Cannot print distribution without \'-p\' option!");
                }

                if(!trainSetPresent && printComplexityStatistics) {
                    throw new Exception("Cannot print complexity statistics (\'-k\') without training file (\'-t\')!");
                }

                if(objectInputFileName.length() != 0) {
                    Utils.checkForRemainingOptions(options);
                } else if(classifier instanceof OptionHandler) {
                    String[] var78 = options;
                    var74 = options.length;

                    for(var75 = 0; var75 < var74; ++var75) {
                        String var80 = var78[var75];
                        if(var80.length() != 0) {
                            if(schemeOptionsText == null) {
                                schemeOptionsText = new StringBuffer();
                            }

                            if(var80.indexOf(32) != -1) {
                                schemeOptionsText.append('\"' + var80 + "\" ");
                            } else {
                                schemeOptionsText.append(var80 + " ");
                            }
                        }
                    }

                    ((OptionHandler)classifier).setOptions(options);
                }

                Utils.checkForRemainingOptions(options);
            } catch (Exception var67) {
                throw new Exception("\nWeka exception: " + var67.getMessage() + makeOptionString((Classifier)classifier, false));
            }

            Evaluation var71 = new Evaluation(new Instances(template, 0), costMatrix);
            Evaluation var79 = new Evaluation(new Instances(template, 0), costMatrix);
            if(!trainSetPresent) {
                var79.useNoPriors();
            }

            if(objectInputFileName.length() != 0) {
                if(objectInputStream != null) {
                    classifier = (Classifier)objectInputStream.readObject();
                    Instances var76 = null;

                    try {
                        var76 = (Instances)objectInputStream.readObject();
                    } catch (Exception var63) {
                        ;
                    }

                    if(var76 != null && !template.equalHeaders(var76)) {
                        throw new Exception("training and test set are not compatible");
                    }

                    objectInputStream.close();
                } else if(xmlInputStream != null) {
                    classifier = (Classifier)KOML.read(xmlInputStream);
                    xmlInputStream.close();
                }
            }

            Classifier classifierBackup = Classifier.makeCopy((Classifier)classifier);
            Instance var81;
            if(classifier instanceof UpdateableClassifier && (testSetPresent || noCrossValidation) && costMatrix == null && trainSetPresent) {
                var71.setPriors(train);
                var79.setPriors(train);
                trainTimeStart = System.currentTimeMillis();
                if(objectInputFileName.length() == 0) {
                    ((Classifier)classifier).buildClassifier(train);
                }

                while(trainSource.hasMoreElements(train)) {
                    var81 = trainSource.nextElement(train);
                    var71.updatePriors(var81);
                    var79.updatePriors(var81);
                    ((UpdateableClassifier)classifier).updateClassifier(var81);
                }

                trainTimeElapsed = System.currentTimeMillis() - trainTimeStart;
            } else if(objectInputFileName.length() == 0) {
                Instances tempTrain = trainSource.getDataSet(actualClassIndex);
                var71.setPriors(tempTrain);
                var79.setPriors(tempTrain);
                trainTimeStart = System.currentTimeMillis();
                ((Classifier)classifier).buildClassifier(tempTrain);
                trainTimeElapsed = System.currentTimeMillis() - trainTimeStart;
            }

            if(objectOutputFileName.length() != 0) {
                Object var83 = new FileOutputStream(objectOutputFileName);
                if(!objectOutputFileName.endsWith(".xml") && (!objectOutputFileName.endsWith(".koml") || !KOML.isPresent())) {
                    if(objectOutputFileName.endsWith(".gz")) {
                        var83 = new GZIPOutputStream((OutputStream)var83);
                    }

                    ObjectOutputStream var84 = new ObjectOutputStream((OutputStream)var83);
                    var84.writeObject(classifier);
                    if(template != null) {
                        var84.writeObject(template);
                    }

                    var84.flush();
                    var84.close();
                } else {
                    BufferedOutputStream var82 = new BufferedOutputStream((OutputStream)var83);
                    if(objectOutputFileName.endsWith(".xml")) {
                        XMLClassifier var85 = new XMLClassifier();
                        var85.write(var82, classifier);
                    } else if(objectOutputFileName.endsWith(".koml")) {
                        KOML.write(var82, classifier);
                    }

                    var82.close();
                }
            }

            if(classifier instanceof Drawable && printGraph) {
                return ((Drawable)classifier).graph();
            } else if(classifier instanceof Sourcable && printSource) {
                return wekaStaticWrapper((Sourcable)classifier, sourceClass);
            } else {
                if(!noOutput && !printMargins) {
                    if(classifier instanceof OptionHandler && schemeOptionsText != null) {
                        text.append("\nOptions: " + schemeOptionsText);
                        text.append("\n");
                    }

                    text.append("\n" + classifier.toString() + "\n");
                }

                if(!printMargins && costMatrix != null) {
                    text.append("\n=== Evaluation Cost Matrix ===\n\n");
                    text.append(costMatrix.toString());
                }

                if(printClassifications) {
                    DataSource var87 = testSource;
                    predsBuff = new StringBuffer();
                    if(testSource == null && noCrossValidation) {
                        var87 = trainSource;
                        predsBuff.append("\n=== Predictions on training data ===\n\n");
                    } else {
                        predsBuff.append("\n=== Predictions on test data ===\n\n");
                    }

                    if(var87 != null) {
                        printClassifications((Classifier)classifier, new Instances(template, 0), var87, actualClassIndex + 1, attributesToOutput, printDistribution, predsBuff);
                    }
                }

                if(trainStatistics && trainSetPresent) {
                    if(classifier instanceof UpdateableClassifier && (testSetPresent || noCrossValidation) && costMatrix == null) {
                        trainSource.reset();
                        train = trainSource.getStructure(actualClassIndex);
                        testTimeStart = System.currentTimeMillis();

                        while(trainSource.hasMoreElements(train)) {
                            var81 = trainSource.nextElement(train);
                            var71.evaluateModelOnce((Classifier)classifier, var81);
                        }

                        testTimeElapsed = System.currentTimeMillis() - testTimeStart;
                    } else {
                        testTimeStart = System.currentTimeMillis();
                        var71.evaluateModel((Classifier)classifier, trainSource.getDataSet(actualClassIndex), new Object[0]);
                        testTimeElapsed = System.currentTimeMillis() - testTimeStart;
                    }

                    if(printMargins) {
                        return var71.toCumulativeMarginDistributionString();
                    }

                    if(!printClassifications) {
                        text.append("\nTime taken to build model: " + Utils.doubleToString((double)trainTimeElapsed / 1000.0D, 2) + " seconds");
                        if(splitPercentage > 0.0D) {
                            text.append("\nTime taken to test model on training split: ");
                        } else {
                            text.append("\nTime taken to test model on training data: ");
                        }

                        text.append(Utils.doubleToString((double)testTimeElapsed / 1000.0D, 2) + " seconds");
                        if(splitPercentage > 0.0D) {
                            text.append(var71.toSummaryString("\n\n=== Error on training split ===\n", printComplexityStatistics));
                        } else {
                            text.append(var71.toSummaryString("\n\n=== Error on training data ===\n", printComplexityStatistics));
                        }

                        if(template.classAttribute().isNominal()) {
                            if(classStatistics) {
                                text.append("\n\n" + var71.toClassDetailsString());
                            }

                            text.append("\n\n" + var71.toMatrixString());
                        }
                    }
                }

                if(testSource != null) {
                    testSource.reset();
                    test = testSource.getStructure(test.classIndex());

                    while(testSource.hasMoreElements(test)) {
                        var81 = testSource.nextElement(test);
                        var79.evaluateModelOnceAndRecordPrediction((Classifier)classifier, var81);
                    }

                    if(splitPercentage > 0.0D) {
                        if(!printClassifications) {
                            text.append("\n\n" + var79.toSummaryString("=== Error on test split ===\n", printComplexityStatistics));
                        }
                    } else if(!printClassifications) {
                        text.append("\n\n" + var79.toSummaryString("=== Error on test data ===\n", printComplexityStatistics));
                    }
                } else if(trainSource != null && !noCrossValidation) {
                    Random var88 = new Random((long)seed);
                    Classifier var68 = Classifier.makeCopy(classifierBackup);
                    if(!printClassifications) {
                        var79.crossValidateModel(var68, trainSource.getDataSet(actualClassIndex), folds, var88, new Object[0]);
                        if(template.classAttribute().isNumeric()) {
                            text.append("\n\n\n" + var79.toSummaryString("=== Cross-validation ===\n", printComplexityStatistics));
                        } else {
                            text.append("\n\n\n" + var79.toSummaryString("=== Stratified cross-validation ===\n", printComplexityStatistics));
                        }
                    } else {
                        predsBuff = new StringBuffer();
                        predsBuff.append("\n=== Predictions under cross-validation ===\n\n");
                        var79.crossValidateModel(var68, trainSource.getDataSet(actualClassIndex), folds, var88, new Object[]{predsBuff, attributesToOutput, new Boolean(printDistribution)});
                    }
                }

                if(template.classAttribute().isNominal() && !printClassifications && (!noCrossValidation || testSource != null)) {
                    if(classStatistics) {
                        text.append("\n\n" + var79.toClassDetailsString());
                    }

                    text.append("\n\n" + var79.toMatrixString());
                }

                if(predsBuff != null) {
                    text.append("\n" + predsBuff);
                }

                if(thresholdFile.length() != 0 && template.classAttribute().isNominal()) {
                    var74 = 0;
                    if(thresholdLabel.length() != 0) {
                        var74 = template.classAttribute().indexOfValue(thresholdLabel);
                    }

                    if(var74 == -1) {
                        throw new IllegalArgumentException("Class label \'" + thresholdLabel + "\' is unknown!");
                    }

                    ThresholdCurve var86 = new ThresholdCurve();
                    result = var86.getCurve(var79.predictions(), var74);
                    DataSink.write(thresholdFile, result);
                }

                return text.toString();
            }
        } else {
            boolean trainingEvaluation = Utils.getFlag("synopsis", options) || Utils.getFlag("info", options);
            throw new Exception("\nHelp requested." + makeOptionString((Classifier)classifier, trainingEvaluation));
        }
    }

    protected static CostMatrix handleCostOption(String costFileName, int numClasses) throws Exception {
        if(costFileName != null && costFileName.length() != 0) {
            System.out.println("NOTE: The behaviour of the -m option has changed between WEKA 3.0 and WEKA 3.1. -m now carries out cost-sensitive *evaluation* only. For cost-sensitive *prediction*, use one of the cost-sensitive metaschemes such as weka.classifiers.meta.CostSensitiveClassifier or weka.classifiers.meta.MetaCost");
            BufferedReader costReader = null;

            try {
                costReader = new BufferedReader(new FileReader(costFileName));
            } catch (Exception var8) {
                throw new Exception("Can\'t open file " + var8.getMessage() + '.');
            }

            try {
                return new CostMatrix(costReader);
            } catch (Exception var7) {
                try {
                    try {
                        costReader.close();
                        costReader = new BufferedReader(new FileReader(costFileName));
                    } catch (Exception var5) {
                        throw new Exception("Can\'t open file " + var5.getMessage() + '.');
                    }

                    CostMatrix e2 = new CostMatrix(numClasses);
                    e2.readOldFormat(costReader);
                    return e2;
                } catch (Exception var6) {
                    throw var7;
                }
            }
        } else {
            return null;
        }
    }

    public double[] evaluateModel(Classifier classifier, Instances data, Object... forPredictionsPrinting) throws Exception {
        StringBuffer buff = null;
        Range attsToOutput = null;
        boolean printDist = false;
        double[] predictions = new double[data.numInstances()];
        if(forPredictionsPrinting.length > 0) {
            buff = (StringBuffer)forPredictionsPrinting[0];
            attsToOutput = (Range)forPredictionsPrinting[1];
            printDist = ((Boolean)forPredictionsPrinting[2]).booleanValue();
        }

        for(int i = 0; i < data.numInstances(); ++i) {
            predictions[i] = this.evaluateModelOnceAndRecordPrediction(classifier, data.instance(i));
            if(buff != null) {
                buff.append(predictionText(classifier, data.instance(i), i, attsToOutput, printDist));
            }
        }

        return predictions;
    }

    public double evaluateModelOnceAndRecordPrediction(Classifier classifier, Instance instance) throws Exception {
        Instance classMissing = (Instance)instance.copy();
        double pred = 0.0D;
        classMissing.setDataset(instance.dataset());
        classMissing.setClassMissing();
        if(this.m_ClassIsNominal) {
            if(this.m_Predictions == null) {
                this.m_Predictions = new FastVector();
            }

            double[] dist = classifier.distributionForInstance(classMissing);
            pred = (double)Utils.maxIndex(dist);
            if(dist[(int)pred] <= 0.0D) {
                pred = Instance.missingValue();
            }

            this.updateStatsForClassifier(dist, instance);
            this.m_Predictions.addElement(new NominalPrediction(instance.classValue(), dist, instance.weight()));
        } else {
            pred = classifier.classifyInstance(classMissing);
            this.updateStatsForPredictor(pred, instance);
        }

        return pred;
    }

    public double evaluateModelOnce(Classifier classifier, Instance instance) throws Exception {
        Instance classMissing = (Instance)instance.copy();
        double pred = 0.0D;
        classMissing.setDataset(instance.dataset());
        classMissing.setClassMissing();
        if(this.m_ClassIsNominal) {
            double[] dist = classifier.distributionForInstance(classMissing);
            pred = (double)Utils.maxIndex(dist);
            if(dist[(int)pred] <= 0.0D) {
                pred = Instance.missingValue();
            }

            this.updateStatsForClassifier(dist, instance);
        } else {
            pred = classifier.classifyInstance(classMissing);
            this.updateStatsForPredictor(pred, instance);
        }

        return pred;
    }

    public double evaluateModelOnce(double[] dist, Instance instance) throws Exception {
        double pred;
        if(this.m_ClassIsNominal) {
            pred = (double)Utils.maxIndex(dist);
            if(dist[(int)pred] <= 0.0D) {
                pred = Instance.missingValue();
            }

            this.updateStatsForClassifier(dist, instance);
        } else {
            pred = dist[0];
            this.updateStatsForPredictor(pred, instance);
        }

        return pred;
    }

    public double evaluateModelOnceAndRecordPrediction(double[] dist, Instance instance) throws Exception {
        double pred;
        if(this.m_ClassIsNominal) {
            if(this.m_Predictions == null) {
                this.m_Predictions = new FastVector();
            }

            pred = (double)Utils.maxIndex(dist);
            if(dist[(int)pred] <= 0.0D) {
                pred = Instance.missingValue();
            }

            this.updateStatsForClassifier(dist, instance);
            this.m_Predictions.addElement(new NominalPrediction(instance.classValue(), dist, instance.weight()));
        } else {
            pred = dist[0];
            this.updateStatsForPredictor(pred, instance);
        }

        return pred;
    }

    public void evaluateModelOnce(double prediction, Instance instance) throws Exception {
        if(this.m_ClassIsNominal) {
            this.updateStatsForClassifier(this.makeDistribution(prediction), instance);
        } else {
            this.updateStatsForPredictor(prediction, instance);
        }

    }

    public FastVector predictions() {
        return this.m_Predictions;
    }

    public static String wekaStaticWrapper(Sourcable classifier, String className) throws Exception {
        StringBuffer result = new StringBuffer();
        String staticClassifier = classifier.toSource(className);
        result.append("// Generated with Weka " + Version.VERSION + "\n");
        result.append("//\n");
        result.append("// This code is public domain and comes with no warranty.\n");
        result.append("//\n");
        result.append("// Timestamp: " + new Date() + "\n");
        result.append("\n");
        result.append("package weka.classifiers;\n");
        result.append("\n");
        result.append("import weka.core.Attribute;\n");
        result.append("import weka.core.Capabilities;\n");
        result.append("import weka.core.Capabilities.Capability;\n");
        result.append("import weka.core.Instance;\n");
        result.append("import weka.core.Instances;\n");
        result.append("import weka.core.RevisionUtils;\n");
        result.append("import weka.classifiers.Classifier;\n");
        result.append("\n");
        result.append("public class WekaWrapper\n");
        result.append("  extends Classifier {\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Returns only the toString() method.\n");
        result.append("   *\n");
        result.append("   * @return a string describing the classifier\n");
        result.append("   */\n");
        result.append("  public String globalInfo() {\n");
        result.append("    return toString();\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Returns the capabilities of this classifier.\n");
        result.append("   *\n");
        result.append("   * @return the capabilities\n");
        result.append("   */\n");
        result.append("  public Capabilities getCapabilities() {\n");
        result.append(((Classifier)classifier).getCapabilities().toSource("result", 4));
        result.append("    return result;\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * only checks the data against its capabilities.\n");
        result.append("   *\n");
        result.append("   * @param i the training data\n");
        result.append("   */\n");
        result.append("  public void buildClassifier(Instances i) throws Exception {\n");
        result.append("    // can classifier handle the data?\n");
        result.append("    getCapabilities().testWithFail(i);\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Classifies the given instance.\n");
        result.append("   *\n");
        result.append("   * @param i the instance to classify\n");
        result.append("   * @return the classification result\n");
        result.append("   */\n");
        result.append("  public double classifyInstance(Instance i) throws Exception {\n");
        result.append("    Object[] s = new Object[i.numAttributes()];\n");
        result.append("    \n");
        result.append("    for (int j = 0; j < s.length; j++) {\n");
        result.append("      if (!i.isMissing(j)) {\n");
        result.append("        if (i.attribute(j).isNominal())\n");
        result.append("          s[j] = new String(i.stringValue(j));\n");
        result.append("        else if (i.attribute(j).isNumeric())\n");
        result.append("          s[j] = new Double(i.value(j));\n");
        result.append("      }\n");
        result.append("    }\n");
        result.append("    \n");
        result.append("    // set class value to missing\n");
        result.append("    s[i.classIndex()] = null;\n");
        result.append("    \n");
        result.append("    return " + className + ".classify(s);\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Returns the revision string.\n");
        result.append("   * \n");
        result.append("   * @return        the revision\n");
        result.append("   */\n");
        result.append("  public String getRevision() {\n");
        result.append("    return RevisionUtils.extract(\"1.0\");\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Returns only the classnames and what classifier it is based on.\n");
        result.append("   *\n");
        result.append("   * @return a short description\n");
        result.append("   */\n");
        result.append("  public String toString() {\n");
        result.append("    return \"Auto-generated classifier wrapper, based on " + classifier.getClass().getName() + " (generated with Weka " + Version.VERSION + ").\\n" + "\" + this.getClass().getName() + \"/" + className + "\";\n");
        result.append("  }\n");
        result.append("\n");
        result.append("  /**\n");
        result.append("   * Runs the classfier from commandline.\n");
        result.append("   *\n");
        result.append("   * @param args the commandline arguments\n");
        result.append("   */\n");
        result.append("  public static void main(String args[]) {\n");
        result.append("    runClassifier(new WekaWrapper(), args);\n");
        result.append("  }\n");
        result.append("}\n");
        result.append("\n");
        result.append(staticClassifier);
        return result.toString();
    }

    public final double numInstances() {
        return this.m_WithClass;
    }

    public final double incorrect() {
        return this.m_Incorrect;
    }

    public final double pctIncorrect() {
        return 100.0D * this.m_Incorrect / this.m_WithClass;
    }

    public final double totalCost() {
        return this.m_TotalCost;
    }

    public final double avgCost() {
        return this.m_TotalCost / this.m_WithClass;
    }

    public final double correct() {
        return this.m_Correct;
    }

    public final double pctCorrect() {
        return 100.0D * this.m_Correct / this.m_WithClass;
    }

    public final double unclassified() {
        return this.m_Unclassified;
    }

    public final double pctUnclassified() {
        return 100.0D * this.m_Unclassified / this.m_WithClass;
    }

    public final double errorRate() {
        return !this.m_ClassIsNominal?Math.sqrt(this.m_SumSqrErr / (this.m_WithClass - this.m_Unclassified)):(this.m_CostMatrix == null?this.m_Incorrect / this.m_WithClass:this.avgCost());
    }

    public final double kappa() {
        double[] sumRows = new double[this.m_ConfusionMatrix.length];
        double[] sumColumns = new double[this.m_ConfusionMatrix.length];
        double sumOfWeights = 0.0D;

        for(int correct = 0; correct < this.m_ConfusionMatrix.length; ++correct) {
            for(int j = 0; j < this.m_ConfusionMatrix.length; ++j) {
                sumRows[correct] += this.m_ConfusionMatrix[correct][j];
                sumColumns[j] += this.m_ConfusionMatrix[correct][j];
                sumOfWeights += this.m_ConfusionMatrix[correct][j];
            }
        }

        double var10 = 0.0D;
        double chanceAgreement = 0.0D;

        for(int i = 0; i < this.m_ConfusionMatrix.length; ++i) {
            chanceAgreement += sumRows[i] * sumColumns[i];
            var10 += this.m_ConfusionMatrix[i][i];
        }

        chanceAgreement /= sumOfWeights * sumOfWeights;
        var10 /= sumOfWeights;
        if(chanceAgreement < 1.0D) {
            return (var10 - chanceAgreement) / (1.0D - chanceAgreement);
        } else {
            return 1.0D;
        }
    }

    public final double correlationCoefficient() throws Exception {
        if(this.m_ClassIsNominal) {
            throw new Exception("Can\'t compute correlation coefficient: class is nominal!");
        } else {
            double correlation = 0.0D;
            double varActual = this.m_SumSqrClass - this.m_SumClass * this.m_SumClass / (this.m_WithClass - this.m_Unclassified);
            double varPredicted = this.m_SumSqrPredicted - this.m_SumPredicted * this.m_SumPredicted / (this.m_WithClass - this.m_Unclassified);
            double varProd = this.m_SumClassPredicted - this.m_SumClass * this.m_SumPredicted / (this.m_WithClass - this.m_Unclassified);
            if(varActual * varPredicted <= 0.0D) {
                correlation = 0.0D;
            } else {
                correlation = varProd / Math.sqrt(varActual * varPredicted);
            }

            return correlation;
        }
    }

    public final double meanAbsoluteError() {
        return this.m_SumAbsErr / (this.m_WithClass - this.m_Unclassified);
    }

    public final double meanPriorAbsoluteError() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumPriorAbsErr / this.m_WithClass;
    }

    public final double relativeAbsoluteError() throws Exception {
        return this.m_NoPriors?0.0D / 0.0:100.0D * this.meanAbsoluteError() / this.meanPriorAbsoluteError();
    }

    public final double rootMeanSquaredError() {
        return Math.sqrt(this.m_SumSqrErr / (this.m_WithClass - this.m_Unclassified));
    }

    public final double rootMeanPriorSquaredError() {
        return this.m_NoPriors?0.0D / 0.0:Math.sqrt(this.m_SumPriorSqrErr / this.m_WithClass);
    }

    public final double rootRelativeSquaredError() {
        return this.m_NoPriors?0.0D / 0.0:100.0D * this.rootMeanSquaredError() / this.rootMeanPriorSquaredError();
    }

    public final double priorEntropy() throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Can\'t compute entropy of class prior: class numeric!");
        } else if(this.m_NoPriors) {
            return 0.0D / 0.0;
        } else {
            double entropy = 0.0D;

            for(int i = 0; i < this.m_NumClasses; ++i) {
                entropy -= this.m_ClassPriors[i] / this.m_ClassPriorsSum * Utils.log2(this.m_ClassPriors[i] / this.m_ClassPriorsSum);
            }

            return entropy;
        }
    }

    public final double KBInformation() throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Can\'t compute K&B Info score: class numeric!");
        } else {
            return this.m_NoPriors?0.0D / 0.0:this.m_SumKBInfo;
        }
    }

    public final double KBMeanInformation() throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Can\'t compute K&B Info score: class numeric!");
        } else {
            return this.m_NoPriors?0.0D / 0.0:this.m_SumKBInfo / (this.m_WithClass - this.m_Unclassified);
        }
    }

    public final double KBRelativeInformation() throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Can\'t compute K&B Info score: class numeric!");
        } else {
            return this.m_NoPriors?0.0D / 0.0:100.0D * this.KBInformation() / this.priorEntropy();
        }
    }

    public final double SFPriorEntropy() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumPriorEntropy;
    }

    public final double SFMeanPriorEntropy() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumPriorEntropy / this.m_WithClass;
    }

    public final double SFSchemeEntropy() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumSchemeEntropy;
    }

    public final double SFMeanSchemeEntropy() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumSchemeEntropy / (this.m_WithClass - this.m_Unclassified);
    }

    public final double SFEntropyGain() {
        return this.m_NoPriors?0.0D / 0.0:this.m_SumPriorEntropy - this.m_SumSchemeEntropy;
    }

    public final double SFMeanEntropyGain() {
        return this.m_NoPriors?0.0D / 0.0:(this.m_SumPriorEntropy - this.m_SumSchemeEntropy) / (this.m_WithClass - this.m_Unclassified);
    }

    public String toCumulativeMarginDistributionString() throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Class must be nominal for margin distributions");
        } else {
            String result = "";
            double cumulativeCount = 0.0D;

            for(int i = 0; i <= k_MarginResolution; ++i) {
                if(this.m_MarginCounts[i] != 0.0D) {
                    cumulativeCount += this.m_MarginCounts[i];
                    double margin = (double)i * 2.0D / (double)k_MarginResolution - 1.0D;
                    result = result + Utils.doubleToString(margin, 7, 3) + ' ' + Utils.doubleToString(cumulativeCount * 100.0D / this.m_WithClass, 7, 3) + '\n';
                } else if(i == 0) {
                    result = Utils.doubleToString(-1.0D, 7, 3) + ' ' + Utils.doubleToString(0.0D, 7, 3) + '\n';
                }
            }

            return result;
        }
    }

    public String toSummaryString() {
        return this.toSummaryString("", false);
    }

    public String toSummaryString(boolean printComplexityStatistics) {
        return this.toSummaryString("=== Summary ===\r\n", printComplexityStatistics);
    }

    public String toSummaryString(String title, boolean printComplexityStatistics) {
        StringBuffer text = new StringBuffer();
        if(printComplexityStatistics && this.m_NoPriors) {
            printComplexityStatistics = false;
            System.err.println("Priors disabled, cannot print complexity statistics!");
        }

        text.append(title + "\r\n");

        try {
            if(this.m_WithClass > 0.0D) {
                if(this.m_ClassIsNominal) {
                    text.append("Correctly Classified Instances     ");
                    text.append(Utils.doubleToString(this.correct(), 12, 4) + "     " + Utils.doubleToString(this.pctCorrect(), 12, 4) + " %\r\n");
                    text.append("Incorrectly Classified Instances   ");
                    text.append(Utils.doubleToString(this.incorrect(), 12, 4) + "     " + Utils.doubleToString(this.pctIncorrect(), 12, 4) + " %\r\n");
                    text.append("Kappa statistic                    ");
                    text.append(Utils.doubleToString(this.kappa(), 12, 4) + "\r\n");
                    if(this.m_CostMatrix != null) {
                        text.append("Total Cost                         ");
                        text.append(Utils.doubleToString(this.totalCost(), 12, 4) + "\r\n");
                        text.append("Average Cost                       ");
                        text.append(Utils.doubleToString(this.avgCost(), 12, 4) + "\r\n");
                    }

                    if(printComplexityStatistics) {
                        text.append("K&B Relative Info Score            ");
                        text.append(Utils.doubleToString(this.KBRelativeInformation(), 12, 4) + " %\r\n");
                        text.append("K&B Information Score              ");
                        text.append(Utils.doubleToString(this.KBInformation(), 12, 4) + " bits");
                        text.append(Utils.doubleToString(this.KBMeanInformation(), 12, 4) + " bits/instance\r\n");
                    }
                } else {
                    text.append("Correlation coefficient            ");
                    text.append(Utils.doubleToString(this.correlationCoefficient(), 12, 4) + "\r\n");
                }

                if(printComplexityStatistics) {
                    text.append("Class complexity | order 0         ");
                    text.append(Utils.doubleToString(this.SFPriorEntropy(), 12, 4) + " bits");
                    text.append(Utils.doubleToString(this.SFMeanPriorEntropy(), 12, 4) + " bits/instance\r\n");
                    text.append("Class complexity | scheme          ");
                    text.append(Utils.doubleToString(this.SFSchemeEntropy(), 12, 4) + " bits");
                    text.append(Utils.doubleToString(this.SFMeanSchemeEntropy(), 12, 4) + " bits/instance\r\n");
                    text.append("Complexity improvement     (Sf)    ");
                    text.append(Utils.doubleToString(this.SFEntropyGain(), 12, 4) + " bits");
                    text.append(Utils.doubleToString(this.SFMeanEntropyGain(), 12, 4) + " bits/instance\r\n");
                }

                text.append("Mean absolute error                ");
                text.append(Utils.doubleToString(this.meanAbsoluteError(), 12, 4) + "\r\n");
                text.append("Root mean squared error            ");
                text.append(Utils.doubleToString(this.rootMeanSquaredError(), 12, 4) + "\r\n");
                if(!this.m_NoPriors) {
                    text.append("Relative absolute error            ");
                    text.append(Utils.doubleToString(this.relativeAbsoluteError(), 12, 4) + " %\r\n");
                    text.append("Root relative squared error        ");
                    text.append(Utils.doubleToString(this.rootRelativeSquaredError(), 12, 4) + " %\r\n");
                }
            }

            if(Utils.gr(this.unclassified(), 0.0D)) {
                text.append("UnClassified Instances             ");
                text.append(Utils.doubleToString(this.unclassified(), 12, 4) + "     " + Utils.doubleToString(this.pctUnclassified(), 12, 4) + " %\r\n");
            }

            text.append("Total Number of Instances          ");
            text.append(Utils.doubleToString(this.m_WithClass, 12, 4) + "\r\n");
            if(this.m_MissingClass > 0.0D) {
                text.append("Ignored Class Unknown Instances            ");
                text.append(Utils.doubleToString(this.m_MissingClass, 12, 4) + "\r\n");
            }
        } catch (Exception var5) {
            System.err.println("Arggh - Must be a bug in Evaluation class");
        }

        return text.toString();
    }

    public String toMatrixString() throws Exception {
        return this.toMatrixString("=== Confusion Matrix ===\r\n");
    }

    public String toMatrixString(String title) throws Exception {
        StringBuffer text = new StringBuffer();
        char[] IDChars = new char[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        boolean fractional = false;
        if(!this.m_ClassIsNominal) {
            throw new Exception("Evaluation: No confusion matrix possible!");
        } else {
            double maxval = 0.0D;

            int i;
            int j;
            for(i = 0; i < this.m_NumClasses; ++i) {
                for(j = 0; j < this.m_NumClasses; ++j) {
                    double current = this.m_ConfusionMatrix[i][j];
                    if(current < 0.0D) {
                        current *= -10.0D;
                    }

                    if(current > maxval) {
                        maxval = current;
                    }

                    double fract = current - Math.rint(current);
                    if(!fractional && Math.log(fract) / Math.log(10.0D) >= -2.0D) {
                        fractional = true;
                    }
                }
            }

            int IDWidth = 1 + Math.max((int)(Math.log(maxval) / Math.log(10.0D) + (double)(fractional?3:0)), (int)(Math.log((double)this.m_NumClasses) / Math.log((double)IDChars.length)));
            text.append(title).append("\r\n");

            for(i = 0; i < this.m_NumClasses; ++i) {
                if(fractional) {
                    text.append(" ").append(this.num2ShortID(i, IDChars, IDWidth - 3)).append("   ");
                } else {
                    text.append(" ").append(this.num2ShortID(i, IDChars, IDWidth));
                }
            }

            text.append("   <-- classified as\r\n");

            for(i = 0; i < this.m_NumClasses; ++i) {
                for(j = 0; j < this.m_NumClasses; ++j) {
                    text.append(" ").append(Utils.doubleToString(this.m_ConfusionMatrix[i][j], IDWidth, fractional?2:0));
                }

                text.append(" | ").append(this.num2ShortID(i, IDChars, IDWidth)).append(" = ").append(this.m_ClassNames[i]).append("\r\n");
            }

            return text.toString();
        }
    }

    public String toClassDetailsString() throws Exception {
        return this.toClassDetailsString("=== Detailed Accuracy By Class ===\r\n");
    }

    public String toClassDetailsString(String title) throws Exception {
        if(!this.m_ClassIsNominal) {
            throw new Exception("Evaluation: No confusion matrix possible!");
        } else {
            StringBuffer text = new StringBuffer(title + "\r\n               TP Rate   FP Rate" + "   Precision   Recall" + "  F-Measure   ROC Area  Class\r\n");

            for(int i = 0; i < this.m_NumClasses; ++i) {
                text.append("               " + Utils.doubleToString(this.truePositiveRate(i), 7, 3)).append("   ");
                text.append(Utils.doubleToString(this.falsePositiveRate(i), 7, 3)).append("    ");
                text.append(Utils.doubleToString(this.precision(i), 7, 3)).append("   ");
                text.append(Utils.doubleToString(this.recall(i), 7, 3)).append("   ");
                text.append(Utils.doubleToString(this.fMeasure(i), 7, 3)).append("    ");
                double rocVal = this.areaUnderROC(i);
                if(Instance.isMissingValue(rocVal)) {
                    text.append("  ?    ").append("    ");
                } else {
                    text.append(Utils.doubleToString(rocVal, 7, 3)).append("    ");
                }

                text.append(this.m_ClassNames[i]).append("\r\n");
            }

            text.append("Weighted Avg.  " + Utils.doubleToString(this.weightedTruePositiveRate(), 7, 3));
            text.append("   " + Utils.doubleToString(this.weightedFalsePositiveRate(), 7, 3));
            text.append("    " + Utils.doubleToString(this.weightedPrecision(), 7, 3));
            text.append("   " + Utils.doubleToString(this.weightedRecall(), 7, 3));
            text.append("   " + Utils.doubleToString(this.weightedFMeasure(), 7, 3));
            text.append("    " + Utils.doubleToString(this.weightedAreaUnderROC(), 7, 3));
            text.append("\r\n");
            return text.toString();
        }
    }

    public double numTruePositives(int classIndex) {
        double correct = 0.0D;

        for(int j = 0; j < this.m_NumClasses; ++j) {
            if(j == classIndex) {
                correct += this.m_ConfusionMatrix[classIndex][j];
            }
        }

        return correct;
    }

    public double truePositiveRate(int classIndex) {
        double correct = 0.0D;
        double total = 0.0D;

        for(int j = 0; j < this.m_NumClasses; ++j) {
            if(j == classIndex) {
                correct += this.m_ConfusionMatrix[classIndex][j];
            }

            total += this.m_ConfusionMatrix[classIndex][j];
        }

        if(total == 0.0D) {
            return 0.0D;
        } else {
            return correct / total;
        }
    }

    public double weightedTruePositiveRate() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int truePosTotal = 0; truePosTotal < this.m_NumClasses; ++truePosTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[truePosTotal] += this.m_ConfusionMatrix[truePosTotal][j];
            }

            classCountSum += classCounts[truePosTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.truePositiveRate(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public double numTrueNegatives(int classIndex) {
        double correct = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i != classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j != classIndex) {
                        correct += this.m_ConfusionMatrix[i][j];
                    }
                }
            }
        }

        return correct;
    }

    public double trueNegativeRate(int classIndex) {
        double correct = 0.0D;
        double total = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i != classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j != classIndex) {
                        correct += this.m_ConfusionMatrix[i][j];
                    }

                    total += this.m_ConfusionMatrix[i][j];
                }
            }
        }

        if(total == 0.0D) {
            return 0.0D;
        } else {
            return correct / total;
        }
    }

    public double weightedTrueNegativeRate() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int trueNegTotal = 0; trueNegTotal < this.m_NumClasses; ++trueNegTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[trueNegTotal] += this.m_ConfusionMatrix[trueNegTotal][j];
            }

            classCountSum += classCounts[trueNegTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.trueNegativeRate(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public double numFalsePositives(int classIndex) {
        double incorrect = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i != classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j == classIndex) {
                        incorrect += this.m_ConfusionMatrix[i][j];
                    }
                }
            }
        }

        return incorrect;
    }

    public double falsePositiveRate(int classIndex) {
        double incorrect = 0.0D;
        double total = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i != classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j == classIndex) {
                        incorrect += this.m_ConfusionMatrix[i][j];
                    }

                    total += this.m_ConfusionMatrix[i][j];
                }
            }
        }

        if(total == 0.0D) {
            return 0.0D;
        } else {
            return incorrect / total;
        }
    }

    public double weightedFalsePositiveRate() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int falsePosTotal = 0; falsePosTotal < this.m_NumClasses; ++falsePosTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[falsePosTotal] += this.m_ConfusionMatrix[falsePosTotal][j];
            }

            classCountSum += classCounts[falsePosTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.falsePositiveRate(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public double numFalseNegatives(int classIndex) {
        double incorrect = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i == classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j != classIndex) {
                        incorrect += this.m_ConfusionMatrix[i][j];
                    }
                }
            }
        }

        return incorrect;
    }

    public double falseNegativeRate(int classIndex) {
        double incorrect = 0.0D;
        double total = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i == classIndex) {
                for(int j = 0; j < this.m_NumClasses; ++j) {
                    if(j != classIndex) {
                        incorrect += this.m_ConfusionMatrix[i][j];
                    }

                    total += this.m_ConfusionMatrix[i][j];
                }
            }
        }

        if(total == 0.0D) {
            return 0.0D;
        } else {
            return incorrect / total;
        }
    }

    public double weightedFalseNegativeRate() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int falseNegTotal = 0; falseNegTotal < this.m_NumClasses; ++falseNegTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[falseNegTotal] += this.m_ConfusionMatrix[falseNegTotal][j];
            }

            classCountSum += classCounts[falseNegTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.falseNegativeRate(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public double recall(int classIndex) {
        return this.truePositiveRate(classIndex);
    }

    public double weightedRecall() {
        return this.weightedTruePositiveRate();
    }

    public double precision(int classIndex) {
        double correct = 0.0D;
        double total = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            if(i == classIndex) {
                correct += this.m_ConfusionMatrix[i][classIndex];
            }

            total += this.m_ConfusionMatrix[i][classIndex];
        }

        if(total == 0.0D) {
            return 0.0D;
        } else {
            return correct / total;
        }
    }

    public double weightedPrecision() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int precisionTotal = 0; precisionTotal < this.m_NumClasses; ++precisionTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[precisionTotal] += this.m_ConfusionMatrix[precisionTotal][j];
            }

            classCountSum += classCounts[precisionTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.precision(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public double fMeasure(int classIndex) {
        double precision = this.precision(classIndex);
        double recall = this.recall(classIndex);
        return precision + recall == 0.0D?0.0D:2.0D * precision * recall / (precision + recall);
    }

    public double weightedFMeasure() {
        double[] classCounts = new double[this.m_NumClasses];
        double classCountSum = 0.0D;

        for(int fMeasureTotal = 0; fMeasureTotal < this.m_NumClasses; ++fMeasureTotal) {
            for(int j = 0; j < this.m_NumClasses; ++j) {
                classCounts[fMeasureTotal] += this.m_ConfusionMatrix[fMeasureTotal][j];
            }

            classCountSum += classCounts[fMeasureTotal];
        }

        double var9 = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double temp = this.fMeasure(i);
            var9 += temp * classCounts[i];
        }

        return var9 / classCountSum;
    }

    public void setPriors(Instances train) throws Exception {
        this.m_NoPriors = false;
        int i;
        if(!this.m_ClassIsNominal) {
            this.m_NumTrainClassVals = 0;
            this.m_TrainClassVals = null;
            this.m_TrainClassWeights = null;
            this.m_PriorErrorEstimator = null;
            this.m_ErrorEstimator = null;

            for(i = 0; i < train.numInstances(); ++i) {
                Instance currentInst = train.instance(i);
                if(!currentInst.classIsMissing()) {
                    this.addNumericTrainClass(currentInst.classValue(), currentInst.weight());
                }
            }
        } else {
            for(i = 0; i < this.m_NumClasses; ++i) {
                this.m_ClassPriors[i] = 1.0D;
            }

            this.m_ClassPriorsSum = (double)this.m_NumClasses;

            for(i = 0; i < train.numInstances(); ++i) {
                if(!train.instance(i).classIsMissing()) {
                    double[] var10000 = this.m_ClassPriors;
                    int var10001 = (int)train.instance(i).classValue();
                    var10000[var10001] += train.instance(i).weight();
                    this.m_ClassPriorsSum += train.instance(i).weight();
                }
            }
        }

    }

    public double[] getClassPriors() {
        return this.m_ClassPriors;
    }

    public void updatePriors(Instance instance) throws Exception {
        if(!instance.classIsMissing()) {
            if(!this.m_ClassIsNominal) {
                if(!instance.classIsMissing()) {
                    this.addNumericTrainClass(instance.classValue(), instance.weight());
                }
            } else {
                double[] var10000 = this.m_ClassPriors;
                int var10001 = (int)instance.classValue();
                var10000[var10001] += instance.weight();
                this.m_ClassPriorsSum += instance.weight();
            }
        }

    }

    public void useNoPriors() {
        this.m_NoPriors = true;
    }

    public boolean equals(Object obj) {
        if(obj != null && obj.getClass().equals(this.getClass())) {
            Evaluation cmp = (Evaluation)obj;
            if(this.m_ClassIsNominal != cmp.m_ClassIsNominal) {
                return false;
            } else if(this.m_NumClasses != cmp.m_NumClasses) {
                return false;
            } else if(this.m_Incorrect != cmp.m_Incorrect) {
                return false;
            } else if(this.m_Correct != cmp.m_Correct) {
                return false;
            } else if(this.m_Unclassified != cmp.m_Unclassified) {
                return false;
            } else if(this.m_MissingClass != cmp.m_MissingClass) {
                return false;
            } else if(this.m_WithClass != cmp.m_WithClass) {
                return false;
            } else if(this.m_SumErr != cmp.m_SumErr) {
                return false;
            } else if(this.m_SumAbsErr != cmp.m_SumAbsErr) {
                return false;
            } else if(this.m_SumSqrErr != cmp.m_SumSqrErr) {
                return false;
            } else if(this.m_SumClass != cmp.m_SumClass) {
                return false;
            } else if(this.m_SumSqrClass != cmp.m_SumSqrClass) {
                return false;
            } else if(this.m_SumPredicted != cmp.m_SumPredicted) {
                return false;
            } else if(this.m_SumSqrPredicted != cmp.m_SumSqrPredicted) {
                return false;
            } else if(this.m_SumClassPredicted != cmp.m_SumClassPredicted) {
                return false;
            } else {
                if(this.m_ClassIsNominal) {
                    for(int i = 0; i < this.m_NumClasses; ++i) {
                        for(int j = 0; j < this.m_NumClasses; ++j) {
                            if(this.m_ConfusionMatrix[i][j] != cmp.m_ConfusionMatrix[i][j]) {
                                return false;
                            }
                        }
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public static void printClassifications(Classifier classifier, Instances train, DataSource testSource, int classIndex, Range attributesToOutput, StringBuffer predsText) throws Exception {
        printClassifications(classifier, train, testSource, classIndex, attributesToOutput, false, predsText);
    }

    protected static void printClassificationsHeader(Instances test, Range attributesToOutput, boolean printDistribution, StringBuffer text) {
        if(test.classAttribute().isNominal()) {
            if(printDistribution) {
                text.append(" inst#     actual  predicted error distribution");
            } else {
                text.append(" inst#     actual  predicted error prediction");
            }
        } else {
            text.append(" inst#     actual  predicted      error");
        }

        if(attributesToOutput != null) {
            attributesToOutput.setUpper(test.numAttributes() - 1);
            text.append(" (");
            boolean first = true;

            for(int i = 0; i < test.numAttributes(); ++i) {
                if(i != test.classIndex() && attributesToOutput.isInRange(i)) {
                    if(!first) {
                        text.append(",");
                    }

                    text.append(test.attribute(i).name());
                    first = false;
                }
            }

            text.append(")");
        }

        text.append("\n");
    }

    public static void printClassifications(Classifier classifier, Instances train, DataSource testSource, int classIndex, Range attributesToOutput, boolean printDistribution, StringBuffer text) throws Exception {
        if(testSource != null) {
            Instances test = testSource.getStructure();
            if(classIndex != -1) {
                test.setClassIndex(classIndex - 1);
            } else if(test.classIndex() == -1) {
                test.setClassIndex(test.numAttributes() - 1);
            }

            printClassificationsHeader(test, attributesToOutput, printDistribution, text);
            int i = 0;
            testSource.reset();

            for(test = testSource.getStructure(test.classIndex()); testSource.hasMoreElements(test); ++i) {
                Instance inst = testSource.nextElement(test);
                text.append(predictionText(classifier, inst, i, attributesToOutput, printDistribution));
            }
        }

    }

    protected static String predictionText(Classifier classifier, Instance inst, int instNum, Range attributesToOutput, boolean printDistribution) throws Exception {
        StringBuffer result = new StringBuffer();
        byte width = 10;
        byte prec = 3;
        Instance withMissing = (Instance)inst.copy();
        withMissing.setDataset(inst.dataset());
        withMissing.setMissing(withMissing.classIndex());
        double predValue = classifier.classifyInstance(withMissing);
        result.append(Utils.padLeft("" + (instNum + 1), 6));
        if(inst.dataset().classAttribute().isNumeric()) {
            if(inst.classIsMissing()) {
                result.append(" " + Utils.padLeft("?", width));
            } else {
                result.append(" " + Utils.doubleToString(inst.classValue(), width, prec));
            }

            if(Instance.isMissingValue(predValue)) {
                result.append(" " + Utils.padLeft("?", width));
            } else {
                result.append(" " + Utils.doubleToString(predValue, width, prec));
            }

            if(!Instance.isMissingValue(predValue) && !inst.classIsMissing()) {
                result.append(" " + Utils.doubleToString(predValue - inst.classValue(), width, prec));
            } else {
                result.append(" " + Utils.padLeft("?", width));
            }
        } else {
            result.append(" " + Utils.padLeft((int)inst.classValue() + 1 + ":" + inst.toString(inst.classIndex()), width));
            if(Instance.isMissingValue(predValue)) {
                result.append(" " + Utils.padLeft("?", width));
            } else {
                result.append(" " + Utils.padLeft((int)predValue + 1 + ":" + inst.dataset().classAttribute().value((int)predValue), width));
            }

            if(!Instance.isMissingValue(predValue) && !inst.classIsMissing() && (int)predValue + 1 != (int)inst.classValue() + 1) {
                result.append("   +  ");
            } else {
                result.append("      ");
            }

            if(printDistribution) {
                if(Instance.isMissingValue(predValue)) {
                    result.append(" ?");
                } else {
                    result.append(" ");
                    double[] dist = classifier.distributionForInstance(withMissing);

                    for(int n = 0; n < dist.length; ++n) {
                        if(n > 0) {
                            result.append(",");
                        }

                        if(n == (int)predValue) {
                            result.append("*");
                        }

                        result.append(Utils.doubleToString(dist[n], prec));
                    }
                }
            } else if(Instance.isMissingValue(predValue)) {
                result.append(" ?");
            } else {
                result.append(" " + Utils.doubleToString(classifier.distributionForInstance(withMissing)[(int)predValue], prec));
            }
        }

        result.append(" " + attributeValuesString(withMissing, attributesToOutput) + "\n");
        return result.toString();
    }

    protected static String attributeValuesString(Instance instance, Range attRange) {
        StringBuffer text = new StringBuffer();
        if(attRange != null) {
            boolean firstOutput = true;
            attRange.setUpper(instance.numAttributes() - 1);

            for(int i = 0; i < instance.numAttributes(); ++i) {
                if(attRange.isInRange(i) && i != instance.classIndex()) {
                    if(firstOutput) {
                        text.append("(");
                    } else {
                        text.append(",");
                    }

                    text.append(instance.toString(i));
                    firstOutput = false;
                }
            }

            if(!firstOutput) {
                text.append(")");
            }
        }

        return text.toString();
    }

    protected static String makeOptionString(Classifier classifier, boolean globalInfo) {
        StringBuffer optionsText = new StringBuffer("");
        optionsText.append("\n\nGeneral options:\n\n");
        optionsText.append("-h or -help\n");
        optionsText.append("\tOutput help information.\n");
        optionsText.append("-synopsis or -info\n");
        optionsText.append("\tOutput synopsis for classifier (use in conjunction  with -h)\n");
        optionsText.append("-t <name of training file>\n");
        optionsText.append("\tSets training file.\n");
        optionsText.append("-T <name of test file>\n");
        optionsText.append("\tSets test file. If missing, a cross-validation will be performed\n");
        optionsText.append("\ton the training data.\n");
        optionsText.append("-c <class index>\n");
        optionsText.append("\tSets index of class attribute (default: last).\n");
        optionsText.append("-x <number of folds>\n");
        optionsText.append("\tSets number of folds for cross-validation (default: 10).\n");
        optionsText.append("-no-cv\n");
        optionsText.append("\tDo not perform any cross validation.\n");
        optionsText.append("-split-percentage <percentage>\n");
        optionsText.append("\tSets the percentage for the train/test set split, e.g., 66.\n");
        optionsText.append("-preserve-order\n");
        optionsText.append("\tPreserves the order in the percentage split.\n");
        optionsText.append("-s <random number seed>\n");
        optionsText.append("\tSets random number seed for cross-validation or percentage split\n");
        optionsText.append("\t(default: 1).\n");
        optionsText.append("-m <name of file with cost matrix>\n");
        optionsText.append("\tSets file with cost matrix.\n");
        optionsText.append("-l <name of input file>\n");
        optionsText.append("\tSets model input file. In case the filename ends with \'.xml\',\n");
        optionsText.append("\ta PMML file is loaded or, if that fails, options are loaded\n");
        optionsText.append("\tfrom the XML file.\n");
        optionsText.append("-d <name of output file>\n");
        optionsText.append("\tSets model output file. In case the filename ends with \'.xml\',\n");
        optionsText.append("\tonly the options are saved to the XML file, not the model.\n");
        optionsText.append("-v\n");
        optionsText.append("\tOutputs no statistics for training data.\n");
        optionsText.append("-o\n");
        optionsText.append("\tOutputs statistics only, not the classifier.\n");
        optionsText.append("-i\n");
        optionsText.append("\tOutputs detailed information-retrieval");
        optionsText.append(" statistics for each class.\n");
        optionsText.append("-k\n");
        optionsText.append("\tOutputs information-theoretic statistics.\n");
        optionsText.append("-p <attribute range>\n");
        optionsText.append("\tOnly outputs predictions for test instances (or the train\n\tinstances if no test instances provided and -no-cv is used),\n\talong with attributes (0 for none).\n");
        optionsText.append("-distribution\n");
        optionsText.append("\tOutputs the distribution instead of only the prediction\n");
        optionsText.append("\tin conjunction with the \'-p\' option (only nominal classes).\n");
        optionsText.append("-r\n");
        optionsText.append("\tOnly outputs cumulative margin distribution.\n");
        if(classifier instanceof Sourcable) {
            optionsText.append("-z <class name>\n");
            optionsText.append("\tOnly outputs the source representation of the classifier,\n\tgiving it the supplied name.\n");
        }

        if(classifier instanceof Drawable) {
            optionsText.append("-g\n");
            optionsText.append("\tOnly outputs the graph representation of the classifier.\n");
        }

        optionsText.append("-xml filename | xml-string\n");
        optionsText.append("\tRetrieves the options from the XML-data instead of the command line.\n");
        optionsText.append("-threshold-file <file>\n");
        optionsText.append("\tThe file to save the threshold data to.\n\tThe format is determined by the extensions, e.g., \'.arff\' for ARFF \n\tformat or \'.csv\' for CSV.\n");
        optionsText.append("-threshold-label <label>\n");
        optionsText.append("\tThe class label to determine the threshold data for\n\t(default is the first label)\n");
        if(classifier instanceof OptionHandler) {
            optionsText.append("\nOptions specific to " + classifier.getClass().getName() + ":\n\n");
            Enumeration ex = classifier.listOptions();

            while(ex.hasMoreElements()) {
                Option option = (Option)ex.nextElement();
                optionsText.append(option.synopsis() + '\n');
                optionsText.append(option.description() + "\n");
            }
        }

        if(globalInfo) {
            try {
                String ex1 = getGlobalInfo(classifier);
                optionsText.append(ex1);
            } catch (Exception var5) {
                ;
            }
        }

        return optionsText.toString();
    }

    protected static String getGlobalInfo(Classifier classifier) throws Exception {
        BeanInfo bi = Introspector.getBeanInfo(classifier.getClass());
        MethodDescriptor[] methods = bi.getMethodDescriptors();
        Object[] args = new Object[0];
        String result = "\nSynopsis for " + classifier.getClass().getName() + ":\n\n";
        MethodDescriptor[] arr$ = methods;
        int len$ = methods.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            MethodDescriptor method = arr$[i$];
            String name = method.getDisplayName();
            Method meth = method.getMethod();
            if(name.equals("globalInfo")) {
                String globalInfo = (String)((String)meth.invoke(classifier, args));
                result = result + globalInfo;
                break;
            }
        }

        return result;
    }

    protected String num2ShortID(int num, char[] IDChars, int IDWidth) {
        char[] ID = new char[IDWidth];

        int i;
        for(i = IDWidth - 1; i >= 0; --i) {
            ID[i] = IDChars[num % IDChars.length];
            num = num / IDChars.length - 1;
            if(num < 0) {
                break;
            }
        }

        --i;

        while(i >= 0) {
            ID[i] = 32;
            --i;
        }

        return new String(ID);
    }

    protected double[] makeDistribution(double predictedClass) {
        double[] result = new double[this.m_NumClasses];
        if(Instance.isMissingValue(predictedClass)) {
            return result;
        } else {
            if(this.m_ClassIsNominal) {
                result[(int)predictedClass] = 1.0D;
            } else {
                result[0] = predictedClass;
            }

            return result;
        }
    }

    protected void updateStatsForClassifier(double[] predictedDistribution, Instance instance) throws Exception {
        int actualClass = (int)instance.classValue();
        if(!instance.classIsMissing()) {
            this.updateMargins(predictedDistribution, actualClass, instance.weight());
            int predictedClass = -1;
            double bestProb = 0.0D;

            for(int predictedProb = 0; predictedProb < this.m_NumClasses; ++predictedProb) {
                if(predictedDistribution[predictedProb] > bestProb) {
                    predictedClass = predictedProb;
                    bestProb = predictedDistribution[predictedProb];
                }
            }

            this.m_WithClass += instance.weight();
            if(this.m_CostMatrix != null) {
                if(predictedClass < 0) {
                    this.m_TotalCost += instance.weight() * this.m_CostMatrix.getMaxCost(actualClass, instance);
                } else {
                    this.m_TotalCost += instance.weight() * this.m_CostMatrix.getElement(actualClass, predictedClass, instance);
                }
            }

            if(predictedClass < 0) {
                this.m_Unclassified += instance.weight();
                return;
            }

            double var11 = Math.max(4.9E-324D, predictedDistribution[actualClass]);
            double priorProb = Math.max(4.9E-324D, this.m_ClassPriors[actualClass] / this.m_ClassPriorsSum);
            if(var11 >= priorProb) {
                this.m_SumKBInfo += (Utils.log2(var11) - Utils.log2(priorProb)) * instance.weight();
            } else {
                this.m_SumKBInfo -= (Utils.log2(1.0D - var11) - Utils.log2(1.0D - priorProb)) * instance.weight();
            }

            this.m_SumSchemeEntropy -= Utils.log2(var11) * instance.weight();
            this.m_SumPriorEntropy -= Utils.log2(priorProb) * instance.weight();
            this.updateNumericScores(predictedDistribution, this.makeDistribution(instance.classValue()), instance.weight());
            this.m_ConfusionMatrix[actualClass][predictedClass] += instance.weight();
            if(predictedClass != actualClass) {
                this.m_Incorrect += instance.weight();
            } else {
                this.m_Correct += instance.weight();
            }
        } else {
            this.m_MissingClass += instance.weight();
        }

    }

    protected void updateStatsForPredictor(double predictedValue, Instance instance) throws Exception {
        if(!instance.classIsMissing()) {
            this.m_WithClass += instance.weight();
            if(Instance.isMissingValue(predictedValue)) {
                this.m_Unclassified += instance.weight();
                return;
            }

            this.m_SumClass += instance.weight() * instance.classValue();
            this.m_SumSqrClass += instance.weight() * instance.classValue() * instance.classValue();
            this.m_SumClassPredicted += instance.weight() * instance.classValue() * predictedValue;
            this.m_SumPredicted += instance.weight() * predictedValue;
            this.m_SumSqrPredicted += instance.weight() * predictedValue * predictedValue;
            if(this.m_ErrorEstimator == null) {
                this.setNumericPriorsFromBuffer();
            }

            double predictedProb = Math.max(this.m_ErrorEstimator.getProbability(predictedValue - instance.classValue()), 4.9E-324D);
            double priorProb = Math.max(this.m_PriorErrorEstimator.getProbability(instance.classValue()), 4.9E-324D);
            this.m_SumSchemeEntropy -= Utils.log2(predictedProb) * instance.weight();
            this.m_SumPriorEntropy -= Utils.log2(priorProb) * instance.weight();
            this.m_ErrorEstimator.addValue(predictedValue - instance.classValue(), instance.weight());
            this.updateNumericScores(this.makeDistribution(predictedValue), this.makeDistribution(instance.classValue()), instance.weight());
        } else {
            this.m_MissingClass += instance.weight();
        }

    }

    protected void updateMargins(double[] predictedDistribution, int actualClass, double weight) {
        double probActual = predictedDistribution[actualClass];
        double probNext = 0.0D;

        for(int margin = 0; margin < this.m_NumClasses; ++margin) {
            if(margin != actualClass && predictedDistribution[margin] > probNext) {
                probNext = predictedDistribution[margin];
            }
        }

        double var12 = probActual - probNext;
        int bin = (int)((var12 + 1.0D) / 2.0D * (double)k_MarginResolution);
        this.m_MarginCounts[bin] += weight;
    }

    protected void updateNumericScores(double[] predicted, double[] actual, double weight) {
        double sumErr = 0.0D;
        double sumAbsErr = 0.0D;
        double sumSqrErr = 0.0D;
        double sumPriorAbsErr = 0.0D;
        double sumPriorSqrErr = 0.0D;

        for(int i = 0; i < this.m_NumClasses; ++i) {
            double diff = predicted[i] - actual[i];
            sumErr += diff;
            sumAbsErr += Math.abs(diff);
            sumSqrErr += diff * diff;
            diff = this.m_ClassPriors[i] / this.m_ClassPriorsSum - actual[i];
            sumPriorAbsErr += Math.abs(diff);
            sumPriorSqrErr += diff * diff;
        }

        this.m_SumErr += weight * sumErr / (double)this.m_NumClasses;
        this.m_SumAbsErr += weight * sumAbsErr / (double)this.m_NumClasses;
        this.m_SumSqrErr += weight * sumSqrErr / (double)this.m_NumClasses;
        this.m_SumPriorAbsErr += weight * sumPriorAbsErr / (double)this.m_NumClasses;
        this.m_SumPriorSqrErr += weight * sumPriorSqrErr / (double)this.m_NumClasses;
    }

    protected void addNumericTrainClass(double classValue, double weight) {
        if(this.m_TrainClassVals == null) {
            this.m_TrainClassVals = new double[100];
            this.m_TrainClassWeights = new double[100];
        }

        if(this.m_NumTrainClassVals == this.m_TrainClassVals.length) {
            double[] temp = new double[this.m_TrainClassVals.length * 2];
            System.arraycopy(this.m_TrainClassVals, 0, temp, 0, this.m_TrainClassVals.length);
            this.m_TrainClassVals = temp;
            temp = new double[this.m_TrainClassWeights.length * 2];
            System.arraycopy(this.m_TrainClassWeights, 0, temp, 0, this.m_TrainClassWeights.length);
            this.m_TrainClassWeights = temp;
        }

        this.m_TrainClassVals[this.m_NumTrainClassVals] = classValue;
        this.m_TrainClassWeights[this.m_NumTrainClassVals] = weight;
        ++this.m_NumTrainClassVals;
    }

    protected void setNumericPriorsFromBuffer() {
        double numPrecision = 0.01D;
        if(this.m_NumTrainClassVals > 1) {
            double[] i = new double[this.m_NumTrainClassVals];
            System.arraycopy(this.m_TrainClassVals, 0, i, 0, this.m_NumTrainClassVals);
            int[] index = Utils.sort(i);
            double lastVal = i[index[0]];
            double deltaSum = 0.0D;
            int distinct = 0;

            for(int i1 = 1; i1 < i.length; ++i1) {
                double current = i[index[i1]];
                if(current != lastVal) {
                    deltaSum += current - lastVal;
                    lastVal = current;
                    ++distinct;
                }
            }

            if(distinct > 0) {
                numPrecision = deltaSum / (double)distinct;
            }
        }

        this.m_PriorErrorEstimator = new KernelEstimator(numPrecision);
        this.m_ErrorEstimator = new KernelEstimator(numPrecision);
        this.m_ClassPriors[0] = this.m_ClassPriorsSum = 0.0D;

        for(int var13 = 0; var13 < this.m_NumTrainClassVals; ++var13) {
            this.m_ClassPriors[0] += this.m_TrainClassVals[var13] * this.m_TrainClassWeights[var13];
            this.m_ClassPriorsSum += this.m_TrainClassWeights[var13];
            this.m_PriorErrorEstimator.addValue(this.m_TrainClassVals[var13], this.m_TrainClassWeights[var13]);
        }

    }

    public String getRevision() {
        return RevisionUtils.extract("$Revision: 10974 $");
    }
}
