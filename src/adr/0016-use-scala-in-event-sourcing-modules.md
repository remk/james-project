# 16. Use scala in event sourcing modules

Date: 2019-12-13

## Status

Proposed

## Context

At the time being James use the scala programming language in some parts of its code base, particularily for implementing the Distributed Task Manager,
which uses the event sourcing modules.

The module `event-store-memory` already uses scala.

## Decision

What is proposed here, is to convert in scala the event sourcing modules.
The modules concerned by this change are : 
  -  `event-sourcing-core`
  -  `event-sourcing-pojo`
  -  `event-store-api`
  -  `event-store-cassandra`

## Rationales

This will help to standardize the `event-*` modules as `event-store-memory` is already written in scala.
This change will avoid interopability concerns with the main consumers of those modules which are already written in scala : see the distributed task manager.
In the long run this will allow to have a stronger typing in those parts of the code and to have a much less verbose code.


## Consequences

We will have to mitigate the pervading of the scala api in the java code base by implementing java facade.
