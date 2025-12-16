# SolverForge WASM Service

A Quarkus REST service that solves and analyzes constraint optimization problems using WASM modules. This is the backend service for [SolverForge](https://solverforge.org/).

## Requirements

- JDK 24 or above
- Maven

## Features

### Core Capabilities

- **Dynamic Class Generation**: Generates Java domain classes and constraint providers at runtime from JSON specifications
- **WASM Integration**: Executes WebAssembly modules via Chicory compiler for high-performance constraint evaluation
- **Full Constraint Streams API**: Comprehensive support for constraint stream operations
- **Score Analysis**: Detailed constraint analysis through the `/analyze` endpoint

### Performance Optimizations

- **WASM Module Caching**: SHA-256 based caching prevents re-parsing identical WASM modules
- **Export Function Caching**: Cached WASM export lookups reduce overhead
- **Predicate Result Caching**: Memoization of predicate evaluation results
- **Geometric List Growth**: O(n) amortized append operations for efficient list handling
- **Memory Layout Optimization**: Aligned field offsets matching Rust's LayoutCalculator

### Constraint Stream Operations

**Stream Transformations:**
- `forEach` - Iterate over planning entities
- `forEachUniquePair` - Process unique pairs with joiners
- `join` - Join streams with custom joiners
- `filter` - Filter elements by predicate
- `map` - Transform stream elements
- `expand` - One-to-many transformations
- `flattenLast` - Flatten nested collections (including primitive int lists)
- `ifExists` / `ifNotExists` - Conditional existence checks
- `complement` - Set complement operations

**Aggregations (via `groupBy`):**
- `count` - Count elements
- `sum` - Sum numeric values
- `min` / `max` - Find minimum/maximum values (with null safety)
- `average` - Calculate averages
- `loadBalance` - Fair load distribution aggregator
- `consecutive` - Consecutive sequence detection
- `connectedRange` - Connected range aggregation
- Meta-aggregators: `compose`, `collectAndThen`, `conditional`

**Joiners:**
- Equal, lessThan, lessThanOrEqual, greaterThan, greaterThanOrEqual joiners
- Overlapping and filtering joiners

### Data Type Support

- Primitives: `int`, `long`, `float`, `double`
- String and collections (including primitive int lists)
- Temporal types: `LocalDate`, `LocalDateTime`
- Score types: `SimpleScore`, `HardSoftScore`, `HardMediumSoftScore`, `HardSoftBigDecimalScore`

### Host Functions

- Auto-generated host functions for domain-specific operations
- String comparison: `hstringEquals`
- List operations: `hlistContainsString`
- Dynamic domain model parsing in host functions

### Observability

- **Health Endpoint**: Service readiness checks via `/health`
- **Solver Statistics**: Response includes detailed metrics:
  - Time spent (milliseconds)
  - Score calculation count and speed
  - Move evaluation count and speed
- **Error Handling**: Full cause chain with stack frames in error responses

## API

### Request Format

A planning problem is structured as follows:

```json
{
    "domain": "DomainMap",
    "constraints": "ConstraintMap",
    "wasm": "Base64String",
    "allocator": "ExportedWasmFunction (int) -> int",
    "deallocator": "ExportedWasmFunction (int) -> void",
    "solutionDeallocator": "Optional[ExportedWasmFunction (int) -> void]",
    "listAccessor": "ListAccessor",
    "termination": "Optional[TerminationConfig]",
    "environmentMode": "Optional[EnvironmentMode]",
    "problem": "String"
}
```

### Example Request

```json
{
   "domain": {
       "Employee": {
           "fields": {"name": {"type": "String"}}
       },
       "Shift": {
           "fields": {
               "start": {"type": "int"},
               "end": {"type": "int"},
               "employee": {
                   "type": "Employee",
                   "accessor": {"getter": "getEmployee", "setter": "setEmployee"},
                   "annotations": [{"annotation": "PlanningVariable", "allowsUnassigned": true}]
               }
           }
       },
       "Schedule": {
           "fields": {
               "employees": {
                   "type": "Employee[]",
                   "accessor": {"getter": "getEmployees", "setter": "setEmployees"},
                   "annotations": [
                       {"annotation": "ProblemFactCollectionProperty"},
                       {"annotation": "ValueRangeProvider"}
                   ]
               },
               "shifts": {
                   "type": "Shift[]",
                   "accessor": {"getter": "getShifts", "setter": "setShifts"},
                   "annotations": [
                       {"annotation": "PlanningEntityCollectionProperty"}
                   ]
               },
               "score": {
                   "type": "SimpleScore",
                   "annotations": [{"annotation": "PlanningScore"}]
               }
           },
           "mapper": {"fromString": "strToSchedule", "toString": "scheduleToStr"}
       }
   },
   "constraints": {
       "penalize unassigned": [
           {"kind": "each", "className": "Shift"},
           {"kind": "filter", "functionName": "unassigned"},
           {"kind": "penalize", "weight": "1"}
       ]
   },
   "wasm": "...",
   "allocator": "alloc",
   "deallocator": "dealloc",
   "listAccessor": {
       "new": "newList",
       "get": "getItem",
       "set": "setItem",
       "length": "size",
       "append": "append",
       "insert": "insert",
       "remove": "remove"
   },
   "problem": "{\"employees\":[...], ...}",
   "environmentMode": "FULL_ASSERT",
   "termination": {"spentLimit": "1s"}
}
```

### Endpoints

#### POST `/solve`

Runs the solver and returns the optimized solution with statistics.

**Response:**
```json
{
  "solution": "{\"employees\":[...], ...}",
  "score": "18",
  "stats": {
    "timeSpentMillis": 1234,
    "scoreCalculationCount": 5678,
    "scoreCalculationSpeed": 4567,
    "moveEvaluationCount": 9012,
    "moveEvaluationSpeed": 7345
  }
}
```

#### POST `/analyze`

Returns constraint analysis for the provided solution.

#### GET `/health`

Service readiness check for monitoring and orchestration.

## Building

```bash
mvn clean package
```

## Running

```bash
java -jar target/solverforge-wasm-service-0.2.0-runner.jar
```

## Attribution

This project is derived from [timefold-wasm-service](https://github.com/Christopher-Chianelli/timefold-wasm-service) by Christopher Chianelli, licensed under the Apache License 2.0.

This project uses [Timefold Solver](https://timefold.ai/) for constraint solving.

## License

Apache License 2.0 - see [LICENSE.txt](LICENSE.txt)
