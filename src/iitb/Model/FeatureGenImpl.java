package iitb.Model;
import iitb.CRF.*;

import java.util.*;
import java.io.*;
/**
 * The FeatureGenerator is an aggregator over all these different
 * feature types. You can inherit from the FeatureGenImpl class and
 * after calling one of the constructors that does not make a call to
 * (addFeatures()) you can then implement your own addFeatures
 * class. There you will typically add the EdgeFeatures feature first
 * and then the rest.  So, for example if you wanted to add some
 * parameter for each label (like a prior), you can create a new
 * FeatureTypes class that will create as many featureids as the
 * number of labels. You will have to create a new class that is
 * derived from FeatureGenImpl and just have a different
 * implementation of the addFeatures subroutine. The rest will be
 * handled by the parent class.  
 * This class  is responsible for converting the
 * string-ids that the FeatureTypes assign to their features into
 * distinct numbers. It has a inner class called FeatureMap that will
 * make one pass over the training data and create the map of
 * featurenames->integer id and as a side effect count the number of
 * features.
 *
 * @author Sunita Sarawagi
 * */

public class FeatureGenImpl implements FeatureGeneratorNested {
    Vector features;
    transient Iterator featureIter;
    protected FeatureTypes currentFeatureType;
    FeatureImpl featureToReturn, feature;
    public Model model;
    int numFeatureTypes=0;
    int totalFeatures;
    
    transient DataSequence data;
    int cposEnd;
    int cposStart;
    WordsInTrain dict;
    
    public void addFeature(FeatureTypes fType) {
        features.add(fType);
    }
    public void setDict(WordsInTrain d) {
        dict = d;
    }
    public WordsInTrain getDict(){
        if (dict == null)
            dict = new WordsInTrain();
        return dict;
    }
    protected void addFeatures() { 
        addFeature(new EdgeFeatures(this));
        addFeature(new StartFeatures(this));
        addFeature(new EndFeatures(this));
        
        dict = new WordsInTrain();
        addFeature(new UnknownFeature(this,dict));
        // addFeature(new KnownInOtherState(model, dict));
        //	addFeature(new KernelFeaturesForLongEntity(model,new WordFeatures(model, dict)));
        addFeature(new WordFeatures(this, dict));
        addFeature(new FeatureTypesEachLabel(this,new ConcatRegexFeatures(this,0,0)));
    }
    protected FeatureTypes getFeature(int i) {
        return (FeatureTypes)features.elementAt(i);
    }
    
    boolean featureCollectMode = false;
    class FeatureMap implements Serializable {
        Hashtable strToInt = new Hashtable();
        FeatureIdentifier idToName[];
        FeatureMap(){
            featureCollectMode = true;
        }
        public int getId(FeatureImpl f) {
            int id = getId(f.identifier());
            if ((id < 0) && featureCollectMode)
                return add(f);
            return id;
        }
        public int getId(Object key) {
            if (strToInt.get(key) != null) {
                return ((Integer)strToInt.get(key)).intValue();
            }
            return -1;
        }
        public int add(FeatureImpl feature) {
            int newId = strToInt.size();
            strToInt.put(feature.identifier().clone(), new Integer(newId));
            return newId;
        }
        void freezeFeatures() {
            //	    System.out.println(strToInt.size());
            featureCollectMode = false;
            idToName = new FeatureIdentifier[strToInt.size()];
            for (Enumeration e = strToInt.keys() ; e.hasMoreElements() ;) {
                Object key = e.nextElement();
                idToName[getId(key)] = (FeatureIdentifier)key;
            }
            totalFeatures = strToInt.size();
        }
        
        public int collectFeatureIdentifiers(DataIter trainData, int maxMem) {
            for (trainData.startScan(); trainData.hasNext();) {
                DataSequence seq = trainData.next();
                for (int l = 0; l < seq.length(); l++) {
                    for (int m = 1; (m <= maxMem) && (l-m >= -1); m++) {
                        for (startScanFeaturesAt(seq,l-m,l); hasNext(); ) {
                            FeatureImpl feature = nextNoId();
                            if (getId(feature) < 0) {
                                add(feature);
                            }
                        }
                    }
                }
            }
            freezeFeatures();
            return strToInt.size();
        }
        public void write(PrintWriter out) throws IOException {
            out.println("******* Features ************");
            out.println(strToInt.size());
            for (Enumeration e = strToInt.keys() ; e.hasMoreElements() ;) {
                Object key = e.nextElement();
                out.println(key + " " + ((Integer)strToInt.get(key)).intValue());
            }
        }
        public int read(BufferedReader in) throws IOException {
            in.readLine();
            int len = Integer.parseInt(in.readLine());
            String line;
            for(int l = 0; (l < len) && ((line=in.readLine())!=null); l++) {
                StringTokenizer entry = new StringTokenizer(line," ");
                FeatureIdentifier key = new FeatureIdentifier(entry.nextToken());
                int pos = Integer.parseInt(entry.nextToken());
                strToInt.put(key,new Integer(pos));
            }
            freezeFeatures();
            return strToInt.size();
        }
        public FeatureIdentifier getIdentifier(int id) {return idToName[id];} 
        public String getName(int id) {return idToName[id].toString();} 
    };
    FeatureMap featureMap;
    static Model getModel(String modelSpecs, int numLabels) throws Exception {
        // create model..
        return Model.getNewModel(numLabels,modelSpecs);
    }
    public FeatureGenImpl(String modelSpecs, int numLabels) throws Exception {
        this(modelSpecs,numLabels,true);
    }
    public FeatureGenImpl(String modelSpecs, int numLabels, boolean addFeatureNow) throws Exception {
        this(getModel(modelSpecs,numLabels),numLabels,addFeatureNow);
    }
    public FeatureGenImpl(Model m, int numLabels, boolean addFeatureNow) throws Exception {
        model = m;
        features = new Vector();
        featureToReturn = new FeatureImpl();
        feature = new FeatureImpl();
        featureMap = new FeatureMap();
        if (addFeatureNow) addFeatures();
    }
    
    public boolean stateMappings(DataIter trainData) throws Exception {
    	if (model.numStates() == model.numberOfLabels())
    		return false;
        for (trainData.startScan(); trainData.hasNext();) {
            DataSequence seq = trainData.next();
            if (seq instanceof SegmentDataSequence) {
                model.stateMappings((SegmentDataSequence)seq);
            } else {
                model.stateMappings(seq);
            }
        }
        return true;
    }
    public boolean mapStatesToLabels(DataSequence data) {
        if (model.numStates() == model.numberOfLabels())
            return false;
        if (data instanceof SegmentDataSequence) {
        	model.mapStatesToLabels((SegmentDataSequence)data);
        } else {
        	for (int i = 0; i < data.length(); i++) {
        		data.set_y(i, label(data.y(i)));
        	}
        }
        return true;
    }
    public int maxMemory() {return 1;}
    public boolean train(DataIter trainData) throws Exception {
        return train(trainData,true);
    }
    public boolean train(DataIter trainData, boolean cachedLabels) throws Exception {
        return train(trainData,cachedLabels,true);
    }
    public boolean labelMappingNeeded() {return model.numStates() != model.numberOfLabels();}
    public boolean train(DataIter trainData, boolean cachedLabels, boolean collectIds) throws Exception {
        // map the y-values in the training set.
        boolean labelsMapped = false;
        if (cachedLabels) {
            labelsMapped = stateMappings(trainData);
        }
        if (dict != null) dict.train(trainData,model.numStates());
        boolean requiresTraining = false;
        for (int f = 0; f < features.size(); f++) {
            if (getFeature(f).requiresTraining()) {
                requiresTraining = true;
                break;
            }
        }
        if (requiresTraining) {
            for (trainData.startScan(); trainData.hasNext();) {
                DataSequence seq = trainData.next();
                for (int l = 0; l < seq.length(); l++) {
                    // train each featuretype.
                    for (int f = 0; f < features.size(); f++) {
                        getFeature(f).train(seq,l);
                    }
                }
            }
        }
        if (collectIds) totalFeatures = featureMap.collectFeatureIdentifiers(trainData,maxMemory());
        return labelsMapped;
    };
    public void printStats() {
        System.out.println("Num states " + model.numStates());
        System.out.println("Num edges " + model.numEdges());
        if (dict != null) System.out.println("Num words in dictionary " + dict.dictionaryLength());
        System.out.println("Num features " + numFeatures());
    }
    protected FeatureImpl nextNoId() {
        feature.copy(featureToReturn);
        advance(false);
        return feature;
    }
    protected void advance() {
        advance(!featureCollectMode);
    }
    protected void advance(boolean returnWithId) {
        while (true) {
            for (;((currentFeatureType == null) || !currentFeatureType.hasNext()) && featureIter.hasNext();) {
                currentFeatureType = (FeatureTypes)featureIter.next();
            }
            if (!currentFeatureType.hasNext())
                break;
            while (currentFeatureType.hasNext()) {
                featureToReturn.init();
                copyNextFeature(featureToReturn);
                
                featureToReturn.id = featureMap.getId(featureToReturn);
                
                if (featureToReturn.id < 0){
                    continue;
                }
                if (featureValid(data, cposStart, cposEnd, featureToReturn, model))
                	return;

            }
        }
        featureToReturn.id = -1;
    }
    /**
     * @param featureToReturn
     */
    protected void copyNextFeature(FeatureImpl featureToReturn) {
        currentFeatureType.next(featureToReturn);
    }
    /**
	 * @param featureToReturn
     * @param cposEnd
     * @param cposStart
     * @param data
     * @return
	 */
	public static boolean featureValid(DataSequence data, int cposStart, int cposEnd, FeatureImpl featureToReturn, Model model) {
        if (((cposStart > 0) && (cposEnd < data.length()-1)) 
                || (featureToReturn.y() >= model.numStates())
                || (featureToReturn.yprev() >= model.numStates()))
            return true;
        if ((cposStart == 0) && (model.isStartState(featureToReturn.y()))
        		&& ((data.length()>1) || (model.isEndState(featureToReturn.y())))) 
            return true;
        if ((cposEnd == data.length()-1) && (model.isEndState(featureToReturn.y())))
            return true;
		return false;
	}
	public void startScanFeaturesAt(DataSequence d, int prev, int p) {
        data = d;
        cposEnd = p;
        cposStart = prev+1;
        for (int i = 0; i < features.size(); i++) {
            getFeature(i).startScanFeaturesAt(data,prev,cposEnd);
        }
        currentFeatureType = null;
        featureIter = features.iterator();
        advance();
    }
    public void startScanFeaturesAt(DataSequence d, int p) {
        data = d;
        cposEnd = p;
        cposStart = p;
        for (int i = 0; i < features.size(); i++) {
            getFeature(i).startScanFeaturesAt(data,cposEnd);
        }
        currentFeatureType = null;
        featureIter = features.iterator();
        advance();
    }
    public boolean hasNext() {
        return (featureToReturn.id >= 0);
    }
    
    public Feature next() {
        feature.copy(featureToReturn);
        advance();
//        System.out.println(feature);
        return feature;
    }
    public void freezeFeatures() {
        if (featureCollectMode)
            featureMap.freezeFeatures();
    }
    public int numFeatures() {
        return totalFeatures;
    }
    public FeatureIdentifier featureIdentifier(int id) {return featureMap.getIdentifier(id);}
    public String featureName(int featureIndex) {
        return featureMap.getName(featureIndex);
    }
    public int numStates() {
        return model.numStates();
    }
    public int label(int stateNum) {
        return model.label(stateNum);
    }
    protected int numFeatureTypes() {
        return features.size();
    }
    public void read(String fileName) throws IOException {
        BufferedReader in=new BufferedReader(new FileReader(fileName));
        if (dict != null) dict.read(in, model.numStates());
        totalFeatures = featureMap.read(in);
    }
    public void write(String fileName) throws IOException {
        PrintWriter out=new PrintWriter(new FileOutputStream(fileName));
        if (dict != null) dict.write(out);
        featureMap.write(out);
        out.close();
    }
    public void displayModel(double featureWts[]) throws IOException {
        displayModel(featureWts,System.out);
    }
    public void displayModel(double featureWts[], PrintStream out) throws IOException {
        for (int fIndex = 0; fIndex < featureWts.length; fIndex++) {
            Object feature = featureIdentifier(fIndex).name;
            int classIndex = featureIdentifier(fIndex).stateId;
            int label = model.label(classIndex);
            out.println(feature + " " + label + " " + classIndex + " " + featureWts[fIndex]);
        }
        /*
         out.println("Feature types statistics");
         for (int f = 0; f < features.size(); f++) {
         getFeature(f).print(featureMap, featureWts);
         }
         */
    }
};
