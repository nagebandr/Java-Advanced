package info.kgeorgiy.ja.Gebesht.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StudentDB implements StudentQuery {
    private static <N, R> R mapStudentsWith(
            Collection<Student> students,
            Function<Student, N> extractor,
            Collector<? super N, ?, R> collector
    ) {
        return students.stream().map(extractor).collect(collector);
    }

    private static List<Student> sortStudentsBy(Collection<Student> students, Comparator<Student> comparator) {
        return students.stream().sorted(comparator).toList();
    }

    private static <R> R filterAndCollect(
            Collection<Student> students,
            Predicate<Student> predicate,
            Comparator<Student> comparator,
            Collector<Student, ?, R> collector
    ) {
        return students.stream()
                .filter(predicate)
                .sorted(comparator)
                .collect(collector);
    }

    private static <R> R filterAndCollect(
            Collection<Student> students,
            Predicate<Student> predicate,
            Collector<Student, ?, R> collector
    ) {
        return students.stream()
                .filter(predicate)
                .collect(collector);
    }

    private static String fullName(final Student student) {
        return student.firstName() + " " + student.lastName();
    }

    private static final Comparator<Student> NAME_COMPARATOR =
            Comparator.comparing(Student::lastName, Comparator.reverseOrder())
                    .thenComparing(Student::firstName, Comparator.reverseOrder())
                    .thenComparingInt(Student::id);

    @Override
    public List<String> getFirstNames(final List<Student> students) {
        return mapStudentsWith(students, Student::firstName, Collectors.toList());
    }

    @Override
    public List<String> getLastNames(final List<Student> students) {
        return mapStudentsWith(students, Student::lastName, Collectors.toList());
    }

    @Override
    public List<GroupName> getGroupNames(final List<Student> students) {
        return mapStudentsWith(students, Student::groupName, Collectors.toList());
    }

    @Override
    public List<String> getFullNames(final List<Student> students) {
        return mapStudentsWith(students, StudentDB::fullName, Collectors.toList());
    }

    @Override
    public Set<String> getDistinctFirstNames(final List<Student> students) {
        return mapStudentsWith(students, Student::firstName,
                Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(final List<Student> students) {
        return students.stream()
                .max(Student::compareTo)
                .map(Student::firstName)
                .orElse("");
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudentsBy(students, Comparator.naturalOrder());
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudentsBy(students, NAME_COMPARATOR);
    }

    @Override
    public List<Student> findStudentsByFirstName(
            Collection<Student> students,
            String name
    ) {
        return filterAndCollect(
                students,
                s -> Objects.equals(s.firstName(), name),
                NAME_COMPARATOR,
                Collectors.toList()
        );
    }

    @Override
    public List<Student> findStudentsByLastName(
            Collection<Student> students,
            String name
    ) {
        return filterAndCollect(
                students,
                s -> Objects.equals(s.lastName(), name),
                NAME_COMPARATOR,
                Collectors.toList()
        );
    }

    @Override
    public List<Student> findStudentsByGroup(
            Collection<Student> students,
            GroupName group
    ) {
        return filterAndCollect(
                students,
                s -> Objects.equals(s.groupName(), group),
                NAME_COMPARATOR,
                Collectors.toList()
        );
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(
            Collection<Student> students,
            GroupName group
    ) {
        return filterAndCollect(
                students,
                s -> Objects.equals(s.groupName(), group),
                Collectors.toMap(
                        Student::lastName,
                        Student::firstName,
                        BinaryOperator.minBy(String::compareTo)
                )
        );
    }
}