DROP TABLE ApprovedEntities IF EXISTS;
DROP TABLE ApprovedNationalities IF EXISTS;
DROP TABLE NDA IF EXISTS;
DROP TABLE Licenses IF EXISTS;
DROP TABLE Entities IF EXISTS;
DROP TABLE Nationalities IF EXISTS;

CREATE TABLE Licenses (
	ID BIGINT PRIMARY KEY, 
	Name VARCHAR(100) UNIQUE NOT NULL, 
	Type VARCHAR(50), 
);

CREATE TABLE Entities (
	ID BIGINT PRIMARY KEY,
	Code VARCHAR(50) UNIQUE NOT NULL,
	Name VARCHAR(255),
	Country VARCHAR(100) NOT NULL
);

CREATE TABLE Nationalities (
	ID BIGINT PRIMARY KEY,
	Name VARCHAR(100) NOT NULL,
	Code VARCHAR(10) NOT NULL
);

CREATE TABLE ApprovedEntities (
	ID BIGINT PRIMARY KEY,
	LicenseID BIGINT NOT NULL,
	EntityID BIGINT NOT NULL,
	Type VARCHAR(1) NOT NULL,
	NDA BIT DEFAULT 0,
	//CONSTRAINT UK_ApprovedEntities UNIQUE (LicenseID, EntityID, Type),
	CONSTRAINT PK_ApprovedEntities_Licenses
			FOREIGN KEY (LicenseID)
			REFERENCES Licenses(ID),
	CONSTRAINT PK_ApprovedEntities_Entities
			FOREIGN KEY (EntityID)
			REFERENCES Entities(ID)
);

CREATE TABLE ApprovedNationalities (
	ID BIGINT PRIMARY KEY,
	LicenseID BIGINT NOT NULL,
	NationalityID BIGINT NOT NULL,
	//CONSTRAINT UK_ApprovedNationalities UNIQUE (LicenseID, NationalityID),
	CONSTRAINT PK_ApprovedNationalities_Licenses
			FOREIGN KEY (LicenseID)
			REFERENCES Licenses(ID),
	CONSTRAINT PK_ApprovedNationalities_Nationalities
			FOREIGN KEY (NationalityID)
			REFERENCES Nationalities(ID)
);

CREATE TABLE DeniedNationalities (
	ID BIGINT PRIMARY KEY,
	LicenseID BIGINT NOT NULL,
	NationalityID BIGINT NOT NULL,
	//CONSTRAINT UK_DeniedNationalities UNIQUE (LicenseID, NationalityID),
	CONSTRAINT PK_DenieidNationalities_Licenses
			FOREIGN KEY (LicenseID)
			REFERENCES Licenses(ID),
	CONSTRAINT PK_DeniedNationalities_Nationalities
			FOREIGN KEY (NationalityID)
			REFERENCES Nationalities(ID)
);

CREATE TABLE NDA (
	ID BIGINT PRIMARY KEY,
	UserID VARCHAR(12) NOT NULL,
	LicenseID BIGINT NOT NULL,
	CONSTRAINT UK_NDA UNIQUE (UserID, LicenseID),
	CONSTRAINT PK_NDA_Licenses
			FOREIGN KEY (LicenseID)
			REFERENCES Licenses(ID)
);