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

CREATE TABLE videoDownloadInf
  (
     vUrl        VARCHAR(200) NOT NULL PRIMARY KEY,
     vDownId     INT,
     vPath       VARCHAR(100),
     vFileSize   BIGINT,
     vSequence   INT,
     vProcessing INT
  );

CREATE TABLE seeds
  (
     seedsUrl VARCHAR(100) NOT NULL PRIMARY KEY,
     seedsStr VARCHAR(50) NOT NULL,
     seedsFlag tinyint(1) NULL
  );
