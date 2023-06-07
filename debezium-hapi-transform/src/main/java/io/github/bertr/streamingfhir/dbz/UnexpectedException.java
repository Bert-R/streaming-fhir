package io.github.bertr.streamingfhir.dbz;

public class UnexpectedException extends RuntimeException
{
    public UnexpectedException(Throwable cause)
    {
        super(cause);
    }
}
