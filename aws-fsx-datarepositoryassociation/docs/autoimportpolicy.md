# AWS::FSx::DataRepositoryAssociation AutoImportPolicy

Specifies the type of updated objects (new, changed, deleted) that will be automatically imported from the linked S3 bucket to your file system.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#events" title="Events">Events</a>" : <i>[ String, ... ]</i>
}
</pre>

### YAML

<pre>
<a href="#events" title="Events">Events</a>: <i>
      - String</i>
</pre>

## Properties

#### Events

_Required_: Yes

_Type_: List of String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
