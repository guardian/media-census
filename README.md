# Media Census

## What is Media Census?

We use computerised Media Asset Management to perform pretty much all media management tasks.  Any piece of media
in our system (and there are now several million) goes through this process:

Loose on SAN (Online) -> Asset Sweeper -> Vidispine -> Copy to nearline -> Archive -> Remove from Online and Nearline

The purpose of Media Census is to get an idea of how much media is in each of these states and if there is some kind
of bottleneck or blockage in the process.

## How does it work?

Media Census is designed with a micro-apps approach in mind.  As a result, there are a number of sbt sub-projects
within this repo.

An external ElasticSearch (6.x) cluster is required to store data.  The fundamental approach is that there is a webapp
which queries the data in the index and presents it to the user, and a number of external scanners which update
the information in the index.  This approach means that it is simple to redeploy any individual component without affecting
the others.

The webapp is a standard Play framework app that performs ES queries and uses Circe to render domain objects into JSON 
for the frontend.
No templating is used, the webapp only renders out JSON and serves a transpiled version of the ReactJS frontend that
exists within the `app/frontend` directory.

The scanners are all based on Akka Streams.  Each is a simple commandline app that instatiates a stream to perform its
work and monitors it until completion.

Each subproject should have its own README within its root directory giving more details about how it works.

### Subprojects

- `webapp` - present in `app/` - this aggregates and presents data from the ElasticSearch cluster. It provides a Scala
backend and a React.js frontend.
- `common` - a library of components that are shared between the different subprojects.  This includes data models for the
ElasticSearch objects, Data Access Objects, client managers, etc.
- `cronscanner` - the first scanner. This performs regular scans of Vidispine items and updates the index with stats
about how many replicas they have (i.e. how much is in nearline)
- `deletescanner` - a cleanup scanner.  This checks the "deleted items" table in Asset Sweeper and removes census entries
for those items that are not present on the SAN.
- `findarchivednearline` - a scanner built for a one-off purpose, checking whether Vidispine thinks that items which are on
nearline are stored in the deep archive
- `fix-orphaned-media` - searches for "placeholder" items in VS (those with no files) and tries to correlate them to content
in deep archive. If found, it updates the VS item with "deep archived" metadata pointing to the found item
- `nearlinescanner` - indexes the contents of the nearline storages as seen by VS. Gathers file sizes and item attachment status.
- `vs-fix-missing-files` - searches for files that Vidispine has logged as "LOST" and reconnects them if possible, or points
the item to an archived copy

## Deployment

The system deploys neatly into Kubernetes.  Elasticseach is a StatefulSet, a Deployment is present for the Webapp and each
scanner is its own cron job.  This keeps maximum separation between the components and allows server resources to be 
smoothly allocated to each component as it needs them.

See the example deployment included. **FIXME** - add example deployment!!