package com.redhat.drools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.internal.dynamic.DynamicEntityImpl;
import org.eclipse.persistence.internal.dynamic.DynamicPropertiesManager;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContextFactory;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

/**
 * DVSDSE - Dynamic very simple drools standalone engine
 * 
 * Author: Kai Wegner - kw at o511.de
 * License: GPL 2.0
 */
public class DVSDSE {
	
	// TODO - Make those configurable
	private static final String CREDIT_APPLICATION_XSD = "/Users/kai/redhat/workspace8/StandaloneDroolsVM/examples/CreditApplication.xsd";
	private static final String CREDIT_APPLICATION_DATA = "/Users/kai/redhat/workspace8/StandaloneDroolsVM/examples/CreditApplication_500k.xml";
	
	private DVSDSEConfig config;
		
	public DVSDSE(DVSDSEConfig config) {
		this.config = config;		
	}
	
    public static final void main(String[] args) throws Throwable {
    	try {
    		// Default values for necessary input parameters
        	DVSDSEConfig defaultConfig = new DVSDSEConfig(
        			new File(CREDIT_APPLICATION_XSD),
        			new File(CREDIT_APPLICATION_DATA),
        			"standaloneSession",
        			"0.0.1",
        			"CreditCheck",
        			new File("/Users/kai/redhat/MyRuleStore")
        	);
        	// -- 
        	
        	// Override default XML-Data programmatically
        	DynamicOverride overrides = new DynamicOverride();
        	overrides.put("amount", new BigDecimal(600000));
        	
        	DynamicOverride myApplicant = new DynamicOverride("Person");
        	myApplicant.put("name", "Andreas Roppel");
        	myApplicant.put("creditScore", 99.5D);
        	
        	overrides.put("applicant", myApplicant);
        	// --
        	
        	// Run the engine and print out the results
        	DynamicEntity creditApplication = new DVSDSE(defaultConfig).run(overrides);

        	System.out.println("Calculated RiskScore for \""+myApplicant.getFromEntity("name")+"\": "+
        			(creditApplication.get("riskScore") == null ? "not granted" :  creditApplication.get("riskScore").toString()));        	
        	// --
    	
    	}
    	catch(Throwable t) {
    		throw t;
    	}
    }
    
    @SuppressWarnings("unused")
	private DynamicEntity run() throws Throwable {
    	return run(null);
    }
    
    private DynamicEntity run(DynamicOverride overrides) throws Throwable {
    	/* 
    	 * Create a dynamic JAXB-Context with EclipseLink for (un)marshalling of objects which are not
    	 * yet in the classpath and can be defined dynamically via 
    	 * CREDIT_APPLICATION_XSD 
    	 * and initiated with
    	 * CREDIT_APPLICATION_DATA
    	 *  
    	 * * Find the documentation here: http://docs.oracle.com/middleware/1212/toplink/TLJAX/dynamic_jaxb.htm#TLJAX448
    	 * * EclipseLink-Project: http://eclipse.org/eclipselink/
    	 */
		FileInputStream xsdFis = new FileInputStream(config.getXSDFile()); 

    	DynamicJAXBContext dynamicJAXBContext = DynamicJAXBContextFactory.createContextFromXSD(
    			xsdFis, null, DVSDSE.class.getClassLoader(), null);

    	@SuppressWarnings("unchecked")
		JAXBElement<DynamicEntity> entityJAXB = 
			(JAXBElement<DynamicEntity>) dynamicJAXBContext.createUnmarshaller().unmarshal(config.getXMLDataFile());
    	DynamicEntity dynamicEntity = entityJAXB.getValue();
    	// --
    	
    	// Replace all overrides
    	if(overrides != null) {
    		overrides.setEntity(dynamicEntity);
    		replaceOverrides(overrides,dynamicJAXBContext);    		
    	}    	
    	// --
    	
    	// Print marshalled object as XML
    	System.out.println("(Un)Marshalled credit application sucessfully:");
    	dynamicJAXBContext.createMarshaller().marshal(dynamicEntity, System.out);
    	System.out.println("---");
    	System.out.println("");
    	// --
    	        	        	
    	// Setting the DynamicJAXB-Classloader as ContextClassLoader is necessary 
    	// for dynamic class resolution within the KIE-Module!
    	Thread.currentThread().setContextClassLoader(dynamicJAXBContext.getDynamicClassLoader());
    	// -- 
    	
        /*
         *  Create a new KIE-Module by create a virtual KieFileSystem and
         *  add files from RULE_STORE_PATH based on RULE_NAME and RULE_VERSION 
         *  (via a simple FileNameFilter - see RuleDirectoryFilter)
         *  
         */
    	KieServices ks = KieServices.Factory.get();
    	KieFileSystem kfs = ks.newKieFileSystem();
    	
    	File[] files = config.getStoreDirectory().listFiles(new RuleDirectoryFilter(config));
    	for(File storeFile : files) {
    		String kieStoreFileName = "src/main/resources/"+config.getSessionName()+"/"+storeFile.getName();

    		System.out.println("# Found: "+storeFile.getName() +" will load: "+kieStoreFileName);
        	
    		kfs.write(kieStoreFileName,ks.getResources().newFileSystemResource(storeFile));        		        		
    	}
    	
    	KieBuilder kBuilder = ks.newKieBuilder(kfs).buildAll();
    	// --
    	
    	// Check for errors after building the KIE-Module 
    	List<org.kie.api.builder.Message> kBuildErrors = kBuilder.getResults().getMessages( org.kie.api.builder.Message.Level.ERROR );
    	if(kBuildErrors.size() > 0) {
    		for(org.kie.api.builder.Message errorMessage : kBuildErrors)
    			System.out.println("# Found ERROR: "+errorMessage.getText());
    		throw new Exception("Could not compile DecisionTable for KIESession.");
    	}
    	else System.out.println("# No Errors found!");
    	System.out.println("");
    	// --
    	
    	// Start new KIE-Session
    	KieContainer kContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    	KieSession kSession = kContainer.newKieSession();        	
    	System.out.println("Starting to insert Objects into KIE-Session ...");
    	// --

    	// Insert the creditApplication object (DynamicEntity) into the KIE-Session-Memory-Context
    	long startTime = System.currentTimeMillis();
        kSession.insert(dynamicEntity);
        long endTime = System.currentTimeMillis();
        // --
        
    	System.out.println("... done (took: "+(endTime-startTime)+" ms)!");
    	System.out.println("Starting Rule Execution... ");
    	
    	// Start the rule execution by firing all rules which may apply 
    	// and print the result
    	startTime = System.currentTimeMillis();
        kSession.fireAllRules();
    	endTime = System.currentTimeMillis();
    	System.out.println("... done (took: "+(endTime-startTime)+" ms)!");
    	
    	return dynamicEntity;
        
    	// --
   		
	}

	private DynamicEntity replaceOverrides(DynamicOverride overrides, DynamicJAXBContext dynamicJAXBContext) {
		Iterator<String> keyIterator = overrides.keySet().iterator();
		
		String key;
		Object value;
		while(keyIterator.hasNext()) {
			key = keyIterator.next();
			value = overrides.get(key);
			
			if(value instanceof DynamicOverride) {
				DynamicOverride sub_override = (DynamicOverride) value;
				DynamicEntity sub_entity = dynamicJAXBContext.newDynamicEntity(sub_override.getEntityType());
				sub_override.setEntity(sub_entity);
				
				value = replaceOverrides(sub_override, dynamicJAXBContext);
			}
			overrides.setInEntity(key, value);
		}
		return overrides.getEntity();
		
	}

	public class RuleDirectoryFilter implements FilenameFilter {
    	private String ruleName;
    	private String ruleVersion;

		public RuleDirectoryFilter(DVSDSEConfig config) {
    		this.ruleName = config.getRuleName();
    		this.ruleVersion = config.getRuleVersion();
		}

		@Override
		public boolean accept(File dir, String name) {
			if(name.contains(ruleName+"_"+ruleVersion))
				return true;
			return false;
		}
    }
    
    public static class DVSDSEConfig {

		private File xsdFile;
		private File dataFile;
		private String sessionName;
		private String ruleVersion;
		private String ruleName;
		private File storeDirectory;

		public DVSDSEConfig(File xsdFile, File dataFile, String sessionName,
				String ruleVersion, String ruleName, File storeDirectory) {
			this.xsdFile = xsdFile;
			this.dataFile = dataFile;
			this.sessionName = sessionName;
			this.ruleVersion = ruleVersion;
			this.ruleName = ruleName;
			this.storeDirectory = storeDirectory;
		}

		public File getXMLDataFile() {
			return dataFile;
		}

		public File getXSDFile() {
			return xsdFile;
		}

		public File getStoreDirectory() {
			return storeDirectory;
		}

		public String getSessionName() {
			return sessionName;
		}

		public String getRuleVersion() {
			return ruleVersion;
		}

		public String getRuleName() {
			return ruleName;
		}
    }
    
    	
    public static class DynamicOverride extends HashMap<String,Object> {
		private static final long serialVersionUID = 1L;
		
		private DynamicEntity entity;
		private String entityType;
    	    	
		public DynamicOverride() {
		}

		public DynamicOverride(String entityType) {
			this.setEntityType(entityType);
		}
		public DynamicEntity getEntity() {
    		return entity;
    	}
    	public void setEntity(DynamicEntity entity) {
    		this.entity = entity;
    	}

		public String getEntityType() {
			return entityType;
		}

		public void setEntityType(String entityType) {
			this.entityType = entityType;
		}
		public String getFromEntity(String key) {
			if(entity != null)
				return entity.get(key);
			else return null;
		}
		public void setInEntity(String key, Object value) {
			if(entity != null)
				entity.set(key,value);
		}
    }
}