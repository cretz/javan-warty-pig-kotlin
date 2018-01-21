## Example: Simple

This example shows how to programmatically invoke the fuzzer and print out the unique branches. It fuzzes a parsing
method that takes a single number format string (i.e. `[+/-]##[.##]`) and returns a simple class of the pieces. It
starts with a single initial value of `+1.2`.

To run, execute the following from the JWP root project directory:

    path/to/gradle --no-daemon :examples:simple:run

After a few seconds, you'll get something like the following:

    Creating fuzzer
    Beginning fuzz
    New path for param '+1.2', result: Ok(value=Num(neg=false, num=1, frac=2))
    New path for param '/1.2', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '+!.2', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '+1/2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: /)
    New path for param '+1."', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '-1.2', result: Ok(value=Num(neg=true, num=1, frac=2))
    New path for param '31.2', result: Ok(value=Num(neg=false, num=31, frac=2))
    New path for param '+162', result: Ok(value=Num(neg=false, num=162, frac=))
    New path for param '+5', result: Ok(value=Num(neg=false, num=5, frac=))
    New path for param '+12', result: Ok(value=Num(neg=false, num=12, frac=))
    New path for param '3', result: Ok(value=Num(neg=false, num=3, frac=))
    New path for param '7 ', result: Failure(ex=java.lang.NumberFormatException: Unknown char:  )
    New path for param '+', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '+11.<2', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '11', result: Ok(value=Num(neg=false, num=11, frac=))
    New path for param '1.2', result: Ok(value=Num(neg=false, num=1, frac=2))
    New path for param '1.2↔1.2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ↔)
    New path for param '+11C2.2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: C)
    New path for param '+11111y111111?2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: y)
    New path for param '1.K?*.2', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '31.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '7372', result: Ok(value=Num(neg=false, num=7372, frac=))
    New path for param '122', result: Ok(value=Num(neg=false, num=122, frac=))
    New path for param '711.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '21?D', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ?)
    New path for param '+111 ?  11111?.1', result: Failure(ex=java.lang.NumberFormatException: Unknown char:  )
    New path for param '2222222222222+12', result: Failure(ex=java.lang.NumberFormatException: Unknown char: +)
    New path for param '+1.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '+111111111111113/111111111111.2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: /)
    New path for param '-?', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '51.§', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '111.1♂*', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ♂)
    New path for param '2222222222222222222231.2', result: Ok(value=Num(neg=false, num=2222222222222222222231, frac=2))
    New path for param '111Q', result: Failure(ex=java.lang.NumberFormatException: Unknown char: Q)
    New path for param '+1.1.☼', result: Failure(ex=java.lang.NumberFormatException: Unknown char: .)
    New path for param '02222222291.2', result: Ok(value=Num(neg=false, num=02222222291, frac=2))
    New path for param '777777777777777777???q????????q', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ?)
    New path for param '222222222291.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '81.82', result: Ok(value=Num(neg=false, num=81, frac=82))
    New path for param '2.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))

While it may look like some paths are repeating, it's important to remember that the number of times a branch is
executed (i.e. "hit count") matters too. Like AFL, hit counts are grouped into buckets of 1, 2, 3, 4-7, 8-15, 16-31,
32-127, and 128+. So different loop counts can be seen as different. This runs until the process is manually killed.

In this example, if the system property `noHitCounts` is present, then hit counts will not be included in the uniqueness 
checks. So, running:

    path/to/gradle --no-daemon :examples:simple:run -DnoHitCounts

Might result in:

    Creating fuzzer
    Beginning fuzz
    New path for param '+1.2', result: Ok(value=Num(neg=false, num=1, frac=2))
    New path for param ';1.2', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '+◄.2', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '+1,2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ,)
    New path for param '7477d?777777777?', result: Failure(ex=java.lang.NumberFormatException: Unknown char: d)
    New path for param '+1."', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '-1.2', result: Ok(value=Num(neg=true, num=1, frac=2))
    New path for param '31.2', result: Ok(value=Num(neg=false, num=31, frac=2))
    New path for param '+162', result: Ok(value=Num(neg=false, num=162, frac=))
    New path for param '+', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '2', result: Ok(value=Num(neg=false, num=2, frac=))
    New path for param '+1.2.2', result: Failure(ex=java.lang.NumberFormatException: Unknown char: .)
    New path for param '+1.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '22.R', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '-/■', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '-1.'', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '-1 2', result: Failure(ex=java.lang.NumberFormatException: Unknown char:  )
    New path for param '1.1(', result: Failure(ex=java.lang.NumberFormatException: Unknown char: ()
    New path for param '200.', result: Failure(ex=java.lang.NumberFormatException: Decimal without trailing number(s))
    New path for param '-', result: Failure(ex=java.lang.NumberFormatException: No leading number(s))
    New path for param '-2', result: Ok(value=Num(neg=true, num=2, frac=))

And then it might appear to hang forever because no matter what is tried, there are no more unique branch paths to
discover.