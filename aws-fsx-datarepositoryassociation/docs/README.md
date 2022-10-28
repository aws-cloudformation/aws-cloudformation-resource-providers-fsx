# AWS::FSx::DataRepositoryAssociation

Resource Type definition for AWS::FSx::DataRepositoryAssociation

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::FSx::DataRepositoryAssociation",
    "Properties" : {
        "<a href="#filesystemid" title="FileSystemId">FileSystemId</a>" : <i>String</i>,
        "<a href="#filesystempath" title="FileSystemPath">FileSystemPath</a>" : <i>String</i>,
        "<a href="#datarepositorypath" title="DataRepositoryPath">DataRepositoryPath</a>" : <i>String</i>,
        "<a href="#batchimportmetadataoncreate" title="BatchImportMetaDataOnCreate">BatchImportMetaDataOnCreate</a>" : <i>Boolean</i>,
        "<a href="#importedfilechunksize" title="ImportedFileChunkSize">ImportedFileChunkSize</a>" : <i>Integer</i>,
        "<a href="#s3" title="S3">S3</a>" : <i><a href="s3.md">S3</a></i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::FSx::DataRepositoryAssociation
Properties:
    <a href="#filesystemid" title="FileSystemId">FileSystemId</a>: <i>String</i>
    <a href="#filesystempath" title="FileSystemPath">FileSystemPath</a>: <i>String</i>
    <a href="#datarepositorypath" title="DataRepositoryPath">DataRepositoryPath</a>: <i>String</i>
    <a href="#batchimportmetadataoncreate" title="BatchImportMetaDataOnCreate">BatchImportMetaDataOnCreate</a>: <i>Boolean</i>
    <a href="#importedfilechunksize" title="ImportedFileChunkSize">ImportedFileChunkSize</a>: <i>Integer</i>
    <a href="#s3" title="S3">S3</a>: <i><a href="s3.md">S3</a></i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### FileSystemId

The globally unique ID of the file system, assigned by Amazon FSx.

_Required_: Yes

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### FileSystemPath

This path specifies where in your file system files will be exported from or imported to. This file system directory can be linked to only one Amazon S3 bucket, and no other S3 bucket can be linked to the directory.

_Required_: Yes

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### DataRepositoryPath

The path to the Amazon S3 data repository that will be linked to the file system. The path can be an S3 bucket or prefix in the format s3://myBucket/myPrefix/ . This path specifies where in the S3 data repository files will be imported from or exported to.

_Required_: Yes

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### BatchImportMetaDataOnCreate

A boolean flag indicating whether an import data repository task to import metadata should run after the data repository association is created. The task runs if this flag is set to true.

_Required_: No

_Type_: Boolean

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### ImportedFileChunkSize

For files imported from a data repository, this value determines the stripe count and maximum amount of data per file (in MiB) stored on a single physical disk. The maximum number of disks that a single file can be striped across is limited by the total number of disks that make up the file system.

_Required_: No

_Type_: Integer

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### S3

The configuration for an Amazon S3 data repository linked to an Amazon FSx Lustre file system with a data repository association. The configuration defines which file events (new, changed, or deleted files or directories) are automatically imported from the linked data repository to the file system or automatically exported from the file system to the data repository.

_Required_: No

_Type_: <a href="s3.md">S3</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

A list of Tag values, with a maximum of 50 elements.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the AssociationId.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### AssociationId

The system-generated, unique ID of the data repository association.

#### ResourceARN

The Amazon Resource Name (ARN) for a given resource. ARNs uniquely identify Amazon Web Services resources. We require an ARN when you need to specify a resource unambiguously across all of Amazon Web Services. For more information, see Amazon Resource Names (ARNs) in the Amazon Web Services General Reference.
