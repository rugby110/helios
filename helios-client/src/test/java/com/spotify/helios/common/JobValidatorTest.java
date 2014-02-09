package com.spotify.helios.common;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.descriptors.PortMapping;

import org.junit.Test;

import static com.google.common.collect.Sets.newHashSet;
import static com.spotify.helios.common.descriptors.Job.EMPTY_COMMAND;
import static com.spotify.helios.common.descriptors.Job.EMPTY_ENV;
import static com.spotify.helios.common.descriptors.Job.EMPTY_PORTS;
import static com.spotify.helios.common.descriptors.Job.EMPTY_REGISTRATION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class JobValidatorTest {

  final JobValidator validator = new JobValidator();

  @Test
  public void testValidJobPasses() {
    final Job job = Job.newBuilder()
        .setName("foo")
        .setVersion("1")
        .setImage("bar")
        .setEnv(ImmutableMap.of("FOO", "BAR"))
        .setPorts(ImmutableMap.of("1", PortMapping.of(1, 1),
                                  "2", PortMapping.of(2, 2)))
        .build();
    assertThat(validator.validate(job), is(empty()));
  }

  @Test
  public void testValidNamesPass() {
    final Job.Builder b = Job.newBuilder().setVersion("1").setImage("bar");
    assertThat(validator.validate(b.setName("foo").build()), is(empty()));
    assertThat(validator.validate(b.setName("17").build()), is(empty()));
    assertThat(validator.validate(b.setName("foo17.bar-baz_quux").build()), is(empty()));
  }

  @Test
  public void testValidVersionsPass() {
    final Job.Builder b = Job.newBuilder().setName("foo").setImage("bar");
    assertThat(validator.validate(b.setVersion("foo").build()), is(empty()));
    assertThat(validator.validate(b.setVersion("17").build()), is(empty()));
    assertThat(validator.validate(b.setVersion("foo17.bar-baz_quux").build()), is(empty()));
  }

  @Test
  public void testValidImagePasses() {
    final Job.Builder b = Job.newBuilder().setName("foo").setVersion("1");
    assertThat(validator.validate(b.setImage("repo").build()), is(empty()));
    assertThat(validator.validate(b.setImage("namespace/repo").build()), is(empty()));
    assertThat(validator.validate(b.setImage("namespace/repo:tag").build()), is(empty()));
    assertThat(validator.validate(b.setImage("namespace/repo:1.2").build()), is(empty()));
    assertThat(validator.validate(b.setImage("reg.istry:4711/repo").build()), is(empty()));
    assertThat(validator.validate(b.setImage("reg.istry.:4711/repo").build()), is(empty()));
    assertThat(validator.validate(b.setImage("reg.istry:4711/namespace/repo").build()),
               is(empty()));
    assertThat(validator.validate(b.setImage("reg.istry.:4711/namespace/repo").build()),
               is(empty()));
    assertThat(validator.validate(b.setImage("1.2.3.4:4711/namespace/repo").build()), is(empty()));
    assertThat(validator.validate(b.setImage("registry.spotify.net:80/spotify/wiggum").build()),
               is(empty()));
    assertThat(validator.validate(b.setImage("registry.spotify.net.:80/spotify/wiggum").build()),
               is(empty()));
  }

  @Test
  public void testPortMappingCollissionFails() throws Exception {
    final Job job = Job.newBuilder()
        .setName("foo")
        .setVersion("1")
        .setImage("bar")
        .setPorts(ImmutableMap.of("1", PortMapping.of(1, 1),
                                  "2", PortMapping.of(2, 1)))
        .build();

    assertEquals(ImmutableSet.of("Duplicate external port mapping: 1"), validator.validate(job));
  }

  @Test
  public void testIdMismatchFails() throws Exception {
    final Job job = new Job(JobId.fromString("foo:bar:badf00d"),
                            "bar", EMPTY_COMMAND, EMPTY_ENV, EMPTY_PORTS, EMPTY_REGISTRATION);
    final JobId recomputedId = job.toBuilder().build().getId();
    assertEquals(ImmutableSet.of("Id mismatch: " + job.getId() + " != " + recomputedId),
                 validator.validate(job));
  }

  @Test
  public void testInvalidNamesFail() throws Exception {
    final Job.Builder b = Job.newBuilder().setVersion("1").setImage("foo");
    assertThat(validator.validate(b.setName("foo@bar").build()),
               contains(equalTo("Job name may only contain [0-9a-zA-Z-_.].")));
    assertThat(validator.validate(b.setName("foo&bar").build()),
               contains(equalTo("Job name may only contain [0-9a-zA-Z-_.].")));
  }

  @Test
  public void testInvalidVersionsFail() throws Exception {
    final Job.Builder b = Job.newBuilder().setName("foo").setImage("foo");
    assertThat(validator.validate(b.setVersion("17@bar").build()),
               contains(equalTo("Job version may only contain [0-9a-zA-Z-_.].")));
    assertThat(validator.validate(b.setVersion("17&bar").build()),
               contains(equalTo("Job version may only contain [0-9a-zA-Z-_.].")));
  }


  @Test
  public void testInvalidImagesFail() throws Exception {
    final Job.Builder b = Job.newBuilder().setName("foo").setVersion("1");

    assertEquals(newHashSet("Tag cannot be empty"),
                 validator.validate(b.setImage("repo:").build()));

    assertFalse(validator.validate(b.setImage("repo:/").build()).isEmpty());

    assertEquals(newHashSet("Invalid domain name: \"1.2.3.4.\""),
                 validator.validate(b.setImage("1.2.3.4.:4711/namespace/repo").build()));

    assertEquals(newHashSet("Invalid domain name: \" reg.istry\""),
                 validator.validate(b.setImage(" reg.istry:4711/repo").build()));

    assertEquals(newHashSet("Invalid domain name: \"reg .istry\""),
                 validator.validate(b.setImage("reg .istry:4711/repo").build()));

    assertEquals(newHashSet("Invalid domain name: \"reg.istry \""),
                 validator.validate(b.setImage("reg.istry :4711/repo").build()));

    assertEquals(newHashSet("Invalid port in endpoint: \"reg.istry: 4711\""),
                 validator.validate(b.setImage("reg.istry: 4711/repo").build()));

    assertEquals(newHashSet("Invalid port in endpoint: \"reg.istry:4711 \""),
                 validator.validate(b.setImage("reg.istry:4711 /repo").build()));

    assertEquals(newHashSet("Invalid repository name ( repo), only [a-z0-9-_.] are allowed"),
                 validator.validate(b.setImage("reg.istry:4711/ repo").build()));

    assertEquals(newHashSet("Invalid namespace name (namespace ), only [a-z0-9_] are " +
                            "allowed, size between 4 and 30"),
                 validator.validate(b.setImage("reg.istry:4711/namespace /repo").build()));

    assertEquals(newHashSet("Invalid repository name ( repo), only [a-z0-9-_.] are allowed"),
                 validator.validate(b.setImage("reg.istry:4711/namespace/ repo").build()));

    assertEquals(newHashSet("Invalid repository name (repo ), only [a-z0-9-_.] are allowed"),
                 validator.validate(b.setImage("reg.istry:4711/namespace/repo ").build()));

    assertEquals(newHashSet("Invalid domain name: \"foo-.ba|z\""),
                 validator.validate(b.setImage("foo-.ba|z/namespace/baz").build()));

    assertEquals(newHashSet("Invalid domain name: \"reg..istry\""),
                 validator.validate(b.setImage("reg..istry/namespace/baz").build()));

    assertEquals(newHashSet("Invalid domain name: \"reg..istry\""),
                 validator.validate(b.setImage("reg..istry/namespace/baz").build()));

    assertEquals(newHashSet("Invalid port in endpoint: \"foo:345345345\""),
                 validator.validate(b.setImage("foo:345345345/namespace/baz").build()));

    assertEquals(newHashSet("Invalid port in endpoint: \"foo:-17\""),
                 validator.validate(b.setImage("foo:-17/namespace/baz").build()));

    assertEquals(newHashSet("Invalid repository name (bar/baz/quux), only [a-z0-9-_.] are allowed"),
                 validator.validate(b.setImage("foos/bar/baz/quux").build()));

    assertEquals(newHashSet("Invalid namespace name (foo), only [a-z0-9_] are allowed, " +
                            "size between 4 and 30"),
                 validator.validate(b.setImage("foo/bar").build()));

    final String foos = Strings.repeat("foo", 100);
    assertEquals(newHashSet("Invalid namespace name (" + foos + "), only [a-z0-9_] are allowed, " +
                            "size between 4 and 30"),
                 validator.validate(b.setImage(foos + "/bar").build()));
  }
}