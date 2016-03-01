SET @@FOREIGN_KEY_CHECKS = 0;

TRUNCATE experiment;
TRUNCATE experiment_statuscode;
TRUNCATE experiment_specimen;
TRUNCATE housing;
TRUNCATE line;
TRUNCATE procedure;
TRUNCATE center_procedure;
TRUNCATE line_statuscode;
TRUNCATE simpleParameter;
TRUNCATE ontologyParameter;
TRUNCATE seriesParameter;
TRUNCATE mediaParameter;
TRUNCATE ontologyParameterTerm;
TRUNCATE seriesParameterValue;
TRUNCATE mediaParameter_parameterAssociation;
TRUNCATE mediaParameter_procedureMetadata;
TRUNCATE parameterAssociation;
TRUNCATE procedureMetadata;
TRUNCATE dimension;
TRUNCATE mediaSampleParameter;
TRUNCATE mediaSample;
TRUNCATE mediaSection;
TRUNCATE mediaFile;
TRUNCATE mediaFile_parameterAssociation;
TRUNCATE mediaFile_procedureMetadata;
TRUNCATE seriesMediaParameter;
TRUNCATE procedure_procedureMetadata;
TRUNCATE seriesMediaParameterValue;
TRUNCATE seriesMediaParameterValue_parameterAssociation;
TRUNCATE seriesMediaParameterValue_procedureMetadata;
 
SET @@FOREIGN_KEY_CHECKS = 1;
