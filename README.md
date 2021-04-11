# Hyperskill calculator

## Console output example

```txt
> 1 + 2
3
> x = 5
> 1 --(3+x)/2
5
> /exit
Bye!
```
## Features

- 4 basic operators ex.: 1 + 2 - 3 * 5 / 10
- variables ex.: x = 4
- parenthesized expressions ex.: (1 + 2 + x)
- only BigInteger operands ex.: (10000001234567890 + 1)

## Purpose

This is my first drill project to get acquainted with Kotlin. It's made for the [hyperskill.org](hyperskill.org) course.

## How to run

```sh
./gradlew run --console=plain
```