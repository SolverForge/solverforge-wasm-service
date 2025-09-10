package ai.timefold.wasm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningVariable;
import ai.timefold.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainValueRangeProvider;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PlanningProblemDeserializationTest {
    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testDeserialize() throws IOException {
        var employee = new DomainObject(
                Map.of(
                        "name", new FieldDescriptor("String", null)
                ), null);
        employee.setName("Employee");

        var shift = new DomainObject(
                Map.of(
                        "start", new FieldDescriptor("int", null),
                        "end", new FieldDescriptor("int", null),
                        "employee", new FieldDescriptor("Employee", new DomainAccessor("getEmployee", "setEmployee"), List.of(new DomainPlanningVariable(true)))
                ), null);
        shift.setName("Shift");

        var schedule = new DomainObject(
                Map.of(
                        "employees", new FieldDescriptor("Employee[]", new DomainAccessor("getEmployees", "setEmployees"), List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())),
                        "shifts", new FieldDescriptor("Shift[]", new DomainAccessor("getShifts", "setShifts"), List.of(new DomainPlanningEntityCollectionProperty())),
                        "score", new FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore()))
                ), new DomainObjectMapper("strToSchedule", "scheduleToStr"));
        schedule.setName("Schedule");

        var penalties = List.of(
                new WasmConstraint("penalize unassigned",
                        "1",
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("unassigned"))
                        ))
        );
        var rewards = List.of(
                new WasmConstraint("reward requested time off",
                        "2",
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("requestedTimeOff"))
                        ))
        );
        var expected = new PlanningProblem(
                Map.of(
                        "Employee", employee,
                        "Shift", shift,
                        "Schedule", schedule
                ),
                penalties,
                rewards,
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                "alloc",
                "dealloc",
                new DomainListAccessor(
                        "newList",
                        "getItem",
                        "setItem",
                        "size",
                        "append",
                        "insert",
                        "remove"
                ),
                "abcd"
        );
        assertThat((Object) objectMapper.readerFor(PlanningProblem.class).readValue(
                   """
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
                                       "annotations": [
                                           {"annotation": "PlanningScore"}
                                       ]
                                   }
                               },
                               "mapper": {"fromString": "strToSchedule", "toString": "scheduleToStr"}
                           }
                       },
                       "penalize": [
                           {
                               "name": "penalize unassigned",
                               "weight": "1",
                               "match": [
                                   {"kind": "each", "className": "Shift"},
                                   {"kind": "filter", "functionName": "unassigned"}
                               ]
                           }
                       ],
                       "reward": [
                           {
                               "name": "reward requested time off",
                               "weight": "2",
                               "match": [
                                   {"kind": "each", "className": "Shift"},
                                   {"kind": "filter", "functionName": "requestedTimeOff"}
                               ]
                           }
                       ],
                       "wasm": "%s",
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
                       "problem": "abcd"
                   }
                   """.formatted(
                           Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})
                   )
        )).usingRecursiveComparison()
          .isEqualTo(expected);
    }
}
