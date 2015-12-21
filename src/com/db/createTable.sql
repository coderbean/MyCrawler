CREATE TABLE videoInfUpdate
  (
     vUrl        VARCHAR(200) NOT NULL PRIMARY KEY,
     vClickRate  INT NULL,
     vComments   INT NULL,
     vUpdateDate DATE NULL
  );

CREATE TABLE videoInf
  (
     vId                   INT NOT NULL PRIMARY KEY  AUTO_INCREMENT,
     vUrl                  VARCHAR(200) NOT NULL,
     vTitle                VARCHAR(100),
     vTitleResult          VARCHAR(100),
     vTag                  VARCHAR(100),
     vTagResult            VARCHAR(100),
     vCommentContent       VARCHAR(50000),
     vCommentContentResult VARCHAR(50000),
     vDatatime             DATE,
     vUploadUser           VARCHAR(100),
     vDownFlag             INT,
     vComeFrom             VARCHAR(10),
     vClass                VARCHAR(30),
     vHot                  INT
  );

CREATE TABLE `videoDownloadInf` (
  `vUrl` varchar(200) NOT NULL,
  `vDownId` int(11) NOT NULL AUTO_INCREMENT,
  `vPath` varchar(1000) DEFAULT NULL,
  `vFileSize` bigint(20) DEFAULT NULL,
  `vSequence` int(11) DEFAULT NULL,
  `vProcessing` int(11) DEFAULT NULL,
  PRIMARY KEY (`vDownId`,`vUrl`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;

CREATE TABLE seeds
  (
     seedsUrl VARCHAR(100) NOT NULL PRIMARY KEY,
     seedsStr VARCHAR(50) NOT NULL,
     seedsFlag tinyint(1) NULL
  );


CREATE TABLE errorUrl
  (
    vUrl         VARCHAR(200) NOT NULL PRIMARY KEY,
    responseCode INT,
    exception    VARCHAR(100),
    ex_datetime  DATE

  );